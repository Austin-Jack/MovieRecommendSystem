package cn.llynsyw.movie.recommender.content.based

import cn.llynsyw.movie.recommender.commons.model.{MongoConfig, Movie, MovieRecs, Recommendation}
import cn.llynsyw.movie.recommender.commons.utils.Compute
import org.apache.spark.SparkConf
import org.apache.spark.ml.feature.{HashingTF, IDF, IDFModel, Tokenizer}
import org.apache.spark.ml.linalg.SparseVector
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.{DataFrame, SparkSession}
import org.jblas.DoubleMatrix

object ContentRecommender {

  // 定义表名和常量
  val MONGODB_MOVIE_COLLECTION = "Movie"

  val CONTENT_MOVIE_RECS = "ContentMovieRecs"

  def main(args: Array[String]): Unit = {
    val config = Map(
      "spark.cores" -> "local[*]",
      "mongo.uri" -> "mongodb://localhost:27017/recommender",
      "mongo.db" -> "recommender"
    )

    val sparkConf: SparkConf = new SparkConf().setMaster(config("spark.cores")).setAppName("OfflineRecommender")

    // 创建一个SparkSession
    val spark: SparkSession = SparkSession.builder().config(sparkConf).getOrCreate()

    import spark.implicits._

    implicit val mongoConfig: MongoConfig = MongoConfig(config("mongo.uri"), config("mongo.db"))

    // 加载数据，并作预处理
    val movieTagsDF: DataFrame = spark.read
      .option("uri", mongoConfig.uri)
      .option("collection", MONGODB_MOVIE_COLLECTION)
      .format("com.mongodb.spark.sql")
      .load()
      .as[Movie]
      .map(
        // 提取mid，name，genres三项作为原始内容特征，分词器默认按照空格做分词
        x => (x.mid, x.name, x.genres.map(c => if (c == '|') ' ' else c))
      )
      .toDF("mid", "name", "genres")
      .cache()

    // 核心部分： 用TF-IDF从内容信息中提取电影特征向量

    // 创建一个分词器，默认按空格分词
    val tokenizer: Tokenizer = new Tokenizer().setInputCol("genres").setOutputCol("words")

    // 用分词器对原始数据做转换，生成新的一列words
    val wordsData: DataFrame = tokenizer.transform(movieTagsDF)

    // 引入HashingTF工具，可以把一个词语序列转化成对应的词频
    val hashingTF: HashingTF = new HashingTF().setInputCol("words").setOutputCol("rawFeatures").setNumFeatures(50)
    val featurizedData: DataFrame = hashingTF.transform(wordsData)

    // 引入IDF工具，可以得到idf模型
    val idf: IDF = new IDF().setInputCol("rawFeatures").setOutputCol("features")
    // 训练idf模型，得到每个词的逆文档频率
    val idfModel: IDFModel = idf.fit(featurizedData)
    // 用模型对原数据进行处理，得到文档中每个词的tf-idf，作为新的特征向量
    val rescaledData: DataFrame = idfModel.transform(featurizedData)

        rescaledData.show(truncate = false)

    val movieFeatures: RDD[(Int, DoubleMatrix)] = rescaledData.map(
      row => (row.getAs[Int]("mid"), row.getAs[SparseVector]("features").toArray)
    )
      .rdd
      .map(
        x => (x._1, new DoubleMatrix(x._2))
      )

    // 对所有电影两两计算它们的相似度，先做笛卡尔积
    val movieRecs: DataFrame = movieFeatures.cartesian(movieFeatures)
      .filter {
        // 把自己跟自己的配对过滤掉
        case (a, b) => a._1 != b._1
      }
      .map {
        case (a, b) => {
          val simScore: Double = Compute.getCosineSim(a._2, b._2)
          (a._1, (b._1, simScore))
        }
      }
      .filter(_._2._2 > 0.6) // 过滤出相似度大于0.6的
      .groupByKey()
      .map {
        case (mid, items) => MovieRecs(mid, items.toList.sortWith(_._2 > _._2).map(x => Recommendation(x._1, x._2)))
      }
      .toDF()
    movieRecs.write
      .option("uri", mongoConfig.uri)
      .option("collection", CONTENT_MOVIE_RECS)
      .mode("overwrite")
      .format("com.mongodb.spark.sql")
      .save()

    spark.stop()
  }
}
