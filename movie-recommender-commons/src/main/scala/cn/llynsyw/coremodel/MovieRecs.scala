package cn.llynsyw.coremodel

/**
 * 定义基于LFM电影特征向量的电影相似度列表
 *
 * @param mid  电影id
 * @param recs 相似矩阵
 */
case class MovieRecs(mid: Int, recs: Seq[Recommendation])
