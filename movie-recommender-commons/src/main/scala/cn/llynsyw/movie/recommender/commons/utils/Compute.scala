package cn.llynsyw.movie.recommender.commons.utils

import breeze.numerics.sqrt
import org.apache.spark.mllib.recommendation.{ALS, MatrixFactorizationModel, Rating}
import org.apache.spark.rdd.RDD
import org.jblas.DoubleMatrix

object Compute {
  // 求向量余弦相似度
  def getCosineSim(movie1: DoubleMatrix, movie2: DoubleMatrix): Double = {
    movie1.dot(movie2) / (movie1.norm2() * movie2.norm2())
  }

  def getRMSE(model: MatrixFactorizationModel, testDataset: RDD[Rating]): Double = {
    // 封装预测矩阵
    val userProducts: RDD[(Int, Int)] = testDataset.map(item => (item.user, item.product))
    // 对预测矩阵进行预测
    val predictResult: RDD[Rating] = model.predict(userProducts)


    val observed: RDD[((Int, Int), Double)] = testDataset.map(ratingMap)
    val predict: RDD[((Int, Int), Double)] = predictResult.map(ratingMap)

    /**
     * 以uid，mid作为外键inner join实际观测值和预测值
     * 得到(uid, mid),(actual, predict)
     */

    sqrt(
      observed
        .join(predict)
        .map {
          case ((_, _), (actual, pre)) =>
            val err: Double = actual - pre
            err * err
        }.mean()
    )
  }

  def getMAE(model: MatrixFactorizationModel, testDataset: RDD[Rating]): Double = {
    // 封装预测矩阵
    val userProducts: RDD[(Int, Int)] = testDataset.map(item => (item.user, item.product))
    // 对预测矩阵进行预测
    val predictResult: RDD[Rating] = model.predict(userProducts)

    val observed: RDD[((Int, Int), Double)] = testDataset.map(ratingMap)
    val predict: RDD[((Int, Int), Double)] = predictResult.map(ratingMap)

    /**
     * 以uid，mid作为外键inner join实际观测值和预测值
     * 得到(uid, mid),(actual, predict)
     */

    observed
      .join(predict)
      .map {
        case ((_, _), (actual, pre)) =>
          val err: Double = math.abs(actual - pre) //计算绝对误差
          err
      }.mean() //计算所有误差的平均值
  }

  /**
   *
   * @param rowData 原始数据，以Rating(user，product，rating)进行封装
   * @param rank 特征维数
   * @param iterations 迭代次数
   * @param lambda 正则化参数
   * @return
   *
   */
  def alsTrain(rowData: RDD[Rating], rank: Int, iterations: Int, lambda: Double): MatrixFactorizationModel = {
    ALS.train(rowData, rank, iterations, lambda)
  }

  /**
   *
   * @param rating Rating对象
   * @return ((用户，物品),评分)
   */
  private def ratingMap(rating: Rating): ((Int, Int), Double) = {
    ((rating.user, rating.product), rating.rating)
  }
}
