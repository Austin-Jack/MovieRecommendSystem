package cn.llynsyw.movie.recommender.offline

import cn.llynsyw.movie.recommender.commons.model._
import cn.llynsyw.movie.recommender.commons.utils.Compute
import org.apache.spark.SparkConf
import org.apache.spark.mllib.recommendation.{ALS, MatrixFactorizationModel, Rating}
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.{DataFrame, SparkSession}
import org.jblas.DoubleMatrix

object OfflineRecommender {

  // 定义表名和常量
  val MONGODB_RATING_COLLECTION = "Rating"

  val USER_RECS = "UserRecs"
  val MOVIE_RECS = "MovieRecs"

  val USER_MAX_RECOMMENDATION = 20

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


    // 加载数据
    val ratingRDD: RDD[(Int, Int, Double)] = spark.read
      .option("uri", mongoConfig.uri)
      .option("collection", MONGODB_RATING_COLLECTION)
      .format("com.mongodb.spark.sql")
      .load()
      .as[MovieRating]
      .rdd
      .map(rating => (rating.uid, rating.mid, rating.score)) // 转化成rdd，并且去掉时间戳
      .cache()

    // 从rating数据中提取所有的uid和mid，并去重
    val userRDD: RDD[Int] = ratingRDD.map(_._1).distinct()
    val movieRDD: RDD[Int] = ratingRDD.map(_._2).distinct()

    // 训练隐语义模型
    val trainData: RDD[Rating] = ratingRDD.map(x => Rating(x._1, x._2, x._3))

    val (rank, iterations, lambda) = (200, 5, 0.1)
    val model: MatrixFactorizationModel = Compute.alsTrain(trainData, rank, iterations, lambda)

    // 基于用户和电影的隐特征，计算预测评分，得到用户的推荐列表
    // 计算user和movie的笛卡尔积，得到一个空评分矩阵
    val userMovies: RDD[(Int, Int)] = userRDD.cartesian(movieRDD)

    // 调用model的predict方法预测评分
    val preRatings: RDD[Rating] = model.predict(userMovies)

    val userRecs: DataFrame = preRatings
      .filter(_.rating > 0) // 过滤出评分大于0的项
      .map(rating => (rating.user, (rating.product, rating.rating)))
      .groupByKey()
      .map {
        case (uid, recs) => UserRecs(uid, recs.toList.sortWith(_._2 > _._2)
          .take(USER_MAX_RECOMMENDATION)
          .map(x => Recommendation(x._1, x._2)))
      }
      .toDF()

    userRecs.write
      .option("uri", mongoConfig.uri)
      .option("collection", USER_RECS)
      .mode("overwrite")
      .format("com.mongodb.spark.sql")
      .save()

    // 基于电影隐特征，计算相似度矩阵，得到电影的相似度列表
    val movieFeatures: RDD[(Int, DoubleMatrix)] = model.productFeatures.map {
      case (mid, features) => (mid, new DoubleMatrix(features))
    }

    // 对所有电影两两计算它们的相似度，先做笛卡尔积
    val movieRecs: DataFrame = movieFeatures
      .cartesian(movieFeatures)
      .filter {
        // 把自己跟自己的配对过滤掉
        case (a, b) => a._1 != b._1
      }
      .map {
        case (a, b) =>
          val simScore: Double = Compute.getCosineSim(a._2, b._2)
          (a._1, (b._1, simScore))
      }
      .filter(_._2._2 > 0.6) // 保留相似度大于0.6的项
      .groupByKey()
      .map {
        case (mid, items) =>
          MovieRecs(mid, items.toList.sortWith(_._2 > _._2)
            .map(x => Recommendation(x._1, x._2)))
      }
      .toDF()
    movieRecs.write
      .option("uri", mongoConfig.uri)
      .option("collection", MOVIE_RECS)
      .mode("overwrite")
      .format("com.mongodb.spark.sql")
      .save()

    spark.stop()
  }
}
