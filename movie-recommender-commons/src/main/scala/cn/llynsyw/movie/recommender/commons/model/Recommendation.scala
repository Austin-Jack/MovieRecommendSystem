package cn.llynsyw.movie.recommender.commons.model

/**
 * 推荐对象
 *
 * @param mid   电影id
 * @param score 电影评分
 */
case class Recommendation(mid: Int, score: Double)
