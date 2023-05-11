package cn.llynsyw.movie.recommender.commons.model

/**
 *
 * @param httpHosts      http主机列表，逗号分隔
 * @param transportHosts transport主机列表
 * @param index          需要操作的索引
 * @param clustername    集群名称，默认elasticsearch
 */
case class ESConfig(httpHosts: String, transportHosts: String, index: String, clustername: String)
