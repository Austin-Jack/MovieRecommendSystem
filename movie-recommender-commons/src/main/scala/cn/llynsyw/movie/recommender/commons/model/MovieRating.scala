package cn.llynsyw.movie.recommender.commons.model

/**
 * 基于评分数据的LFM，只需要rating数据
 *
 * @param uid       用户id
 * @param mid       电影id
 * @param score     电影评分
 * @param timestamp 时间戳
 */
case class MovieRating(uid: Int, mid: Int, score: Double, timestamp: Int)
