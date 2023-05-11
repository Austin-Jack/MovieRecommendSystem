package cn.llynsyw.movie.recommender.commons.model

/**
 * Rating数据集
 *
 * 1,31,2.5,1260759144
 */
case class Rating(uid: Int, mid: Int, score: Double, timestamp: Int)
