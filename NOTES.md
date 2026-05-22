# NOTES.md

## 选做 A：出库单 + 库存扣减 — 并发控制方案

### 方案选择：SQL 原子更新
核心扣减语句：
```sql
UPDATE inventory
SET quantity = quantity - :delta, updated_at = CURRENT_TIMESTAMP
WHERE product_id = :pid AND location_code = :loc AND quantity >= :delta
```

### 为什么选这个方案

 **1.原子更新** 一条 UPDATE 同时完成校验+扣减，无锁竞争、无重试、一条 SQL 完成，只返回 affected rows，需额外查询获取友好错误信息 

**2. 为什么不用悲观锁**

并发性能差

**3. 为什么不用乐观锁**

库存扣减是热点数据——多个出库请求频繁操作同一 `product_id + location_code` 的库存行：
- 乐观锁在冲突时需要重试，每次重试都要重新加载实体、比较版本号、再次尝试更新，在高并发下重试次数可能很多
- 原子更新一次就完成，不需要重试循环
```

```

### 并发安全保证机制

**1. 防超卖**
- `WHERE quantity >= :delta` 条件确保只有库存充足时才能扣减成功
- 数据库行级排他锁保证同一行的并发 UPDATE 串行执行，不会出现"两个事务都读到 100、各扣 50、最终写入 50"的丢失更新

**2. 防事务部分失败**
- `@Transactional` 保证整个出库单创建在同一个事务中
- 如果多明细项中第 N 项库存不足导致 `affected rows = 0`，抛出 BusinessException，整个事务回滚，前面已扣减的库存恢复原值

**3. 防单号竞态**
- 出库单号生成方法使用 `synchronized` 关键字
- 单实例使用`synchronized` 足够保证单号唯一
- 如果需要多实例部署，应使用 Redis 原子递增

**4. 防明细重复**
- 出库前校验 `productId + locationCode` 组合去重，拒绝同一商品在同一库位出现两条明细

### 测试验证

7 个测试用例全部通过，关键测试：

| 测试 | 验证点 |
|------|--------|
| 正常出库 | 库存正确扣减 |
| 库存不足 | 返回友好错误，库存不变 |
| 商品不存在 | 抛出 BusinessException |
| 库位不存在 | 抛出 BusinessException |
| 重复明细 | 拒绝同一商品+库位组合 |
| 部分失败回滚 | 第二项失败时，第一项已扣减的库存恢复 |
| **并发超卖** | 10 线程各出库 15（总计需 150），库存仅 100，最终库存不为负，成功次数 * 15 = 实际扣减量 |