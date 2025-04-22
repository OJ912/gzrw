import request from "@/utils/request";

/**
 * 分页查询校长信箱列表
 * @param {Object} query 查询参数
 * @returns {Promise}
 *
 * 说明：
 * 1. 基本查询：/mailbox/list?pageNum=1&pageSize=10
 * 2. 使用queryType查询：
 *    - queryType=1：查询已审核信箱
 *    - queryType=2：查询未审核信箱
 */
export function listMailbox(query) {
  return request({
    url: "/mailbox/list",
    method: "get",
    params: query,
  });
}

/**
 * 获取未审核的校长信箱列表
 * @param {Object} query 查询参数
 * @returns {Promise}
 */
export function listUnreviewedMailbox(query) {
  return request({
    url: "/mailbox/unreviewed",
    method: "get",
    params: query,
  });
}

/**
 * 获取已审核的校长信箱列表
 * @param {Object} query 查询参数
 * @returns {Promise}
 */
export function listReviewedMailbox(query) {
  return request({
    url: "/mailbox/list",
    method: "get",
    params: { ...query, queryType: 1 },
  });
}

/**
 * 获取单条校长信箱信息
 * @param {Number} id 信箱ID
 * @returns {Promise}
 */
export function getMailbox(id) {
  return request({
    url: `/mailbox/${id}`,
    method: "get",
  });
}

/**
 * 提交校长信箱
 * @param {Object} data 信箱数据
 * @returns {Promise}
 */
export function submitMailbox(data) {
  return request({
    url: "/api/mailbox/submit", //在生产环境下，baseUrl是http://localhost:8090/prod-api    再加上 /api/mailbox/submit 这个项目实际没有用到这个api
    method: "post",
    data: data,
  });
}

/**
 * 更新校长信箱审核状态
 * @param {Number} id 信箱ID
 * @param {Boolean} isReviewed 是否已审核
 * @returns {Promise}
 *
 * 注意：此API需要Bearer Token授权
 */
export function reviewMailbox(id, isReviewed) {
  return request({
    url: `/mailbox/review/${id}`,
    method: "put",
    params: { isReviewed },
  });
}

/**
 * 删除校长信箱
 * @param {Array} ids 信箱ID数组
 * @returns {Promise}
 *
 * 注意：此API需要Bearer Token授权
 */
export function deleteMailbox(ids) {
  return request({
    url: "/mailbox",
    method: "delete",
    data: ids,
  });
}
