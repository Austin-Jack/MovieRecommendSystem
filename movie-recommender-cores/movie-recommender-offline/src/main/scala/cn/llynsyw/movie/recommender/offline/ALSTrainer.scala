package cn.llynsyw.movie.recommender.offline

import cn.llynsyw.movie.recommender.commons.model.{MongoConfig, MovieRating}
import cn.llynsyw.movie.recommender.commons.utils.Compute
import cn.llynsyw.movie.recommender.offline.OfflineRecommender.MONGODB_RATING_COLLECTION
import org.apache.spark.SparkConf
import org.apache.spark.mllib.recommendation.{MatrixFactorizationModel, Rating}
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession

object ALSTrainer {
  def main(args: Array[String]): Unit = {
    val config = Map(
      "spark.cores" -> "local[*]",
      "mongo.uri" -> "mongodb://localhost:27017/recommender",
      "mongo.db" -> "recommender"
    )

    val sparkConf: SparkConf = new SparkConf().setMaster(config("spark.cores")).setAppName("OfflineRecommender")

    // 创建一个SparkSession
    val spark: SparkSession = SparkSession.builder().config(sparkConf).getOrCreate()

    implicit val mongoConfig: MongoConfig = MongoConfig(config("mongo.uri"), config("mongo.db"))

    import spark.implicits._

    // 加载评分数据
    val ratingRDD: RDD[Rating] = spark.read
      .option("uri", mongoConfig.uri)
      .option("collection", MONGODB_RATING_COLLECTION)
      .format("com.mongodb.spark.sql")
      .load()
      .as[MovieRating]
      .rdd
      .map(rating => Rating(rating.uid, rating.mid, rating.score)) // 转化成rdd，并且去掉时间戳
      .cache()

    // 随机切分数据集，生成训练集和测试集
    val splits: Array[RDD[Rating]] = ratingRDD.randomSplit(Array(0.8, 0.2))
    val trainingRDD: RDD[Rating] = splits(0)
    val testRDD: RDD[Rating] = splits(1)

    // 模型参数选择，输出最优参数
    adjustALSParam(trainingRDD, testRDD)

    spark.close()
  }

  def adjustALSParam(trainData: RDD[Rating], testDataset: RDD[Rating]): Unit = {
    val result: Array[(Int, Double, Double)] = for (rank <- Array(50, 100, 200, 300); lambda <- Array(0.01, 0.1, 1))
      yield {
        val model: MatrixFactorizationModel = Compute.alsTrain(trainData, rank, 5, lambda)
        // 计算当前参数对应模型的RMSE，返回Double
        val rmse: Double = Compute.getRMSE(model, testDataset)
        val mae: Double = Compute.getMAE(model, testDataset)
        println("tuple:" + ((rank, lambda), (rmse, mae)))
        (rank, lambda, rmse)
      }
    // 控制台打印输出最优参数
    println(result.minBy(_._3))
  }
}
