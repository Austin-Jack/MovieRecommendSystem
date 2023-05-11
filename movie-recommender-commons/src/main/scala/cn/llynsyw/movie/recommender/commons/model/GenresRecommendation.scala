package cn.llynsyw.movie.recommender.commons.model

/**
 * 电影类别top10推荐对象
 *
 * @param genres 类别
 * @param recs   推荐矩阵
 */
case class GenresRecommendation(genres: String, recs: Seq[Recommendation])
