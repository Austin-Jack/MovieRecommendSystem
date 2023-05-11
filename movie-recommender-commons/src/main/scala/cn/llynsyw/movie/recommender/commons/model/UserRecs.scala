package cn.llynsyw.movie.recommender.commons.model

/**
 *
 * 定义基于预测评分的用户推荐列表
 *
 * @param uid  用户id
 * @param recs 推荐矩阵
 */
case class UserRecs(uid: Int, recs: Seq[Recommendation])
