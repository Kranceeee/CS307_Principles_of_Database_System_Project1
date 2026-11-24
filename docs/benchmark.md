# 主键查找基准实验

这个实验对照逐 ID 执行 `recipeid = ?` 和用同一组 ID 执行 `recipeid = ANY (?)`。两边都使用相同的 `SELECT recipeid` 投影、同一数据库连接池和相同的并发线程数，客户端完整消费结果。实验适合分析 SQL 次数、请求分组和端到端耗时的取舍。

```powershell
java -jar target/recipe-data-lab-1.0.0.jar benchmark 10001 25 4 3
```

1. 读取数据库内已存在的菜谱主键，使用固定种子 `307` 打乱顺序。一次组内的主键互异。样本数超过总主键数时循环取样，最后一组可以短于指定大小。
2. 两种模式均先预热。每轮在同一固定负载上分别运行，并交替先执行的模式，减弱顺序带来的影响。每个工作线程复用自己借出的连接和预编译语句。
3. 每组校验结果与预期 ID 集合完全相同，实际成功执行一次才累计一次 SQL。SQL 异常、缺失或重复 ID 会使命令退出失败。失败轮不会输出成功统计。
4. 记录实际执行次数和组时延。若更改机器、连接池、数据集、并发量或改为远程 DB，应在报告里标明。

| 输出 | 口径 |
|---|---|
| `groups` | 已成功校验的请求组数，两种模式组定义相同 |
| `verified_ids` | 各组读到并核对成功的 ID 数，同一个 ID 可在不同组重复计入 |
| `completed_sql` | JDBC 实际成功执行并读取结果的 SQL 次数 |
| `wall_ms` | 整个测量轮的现实用时，包含客户端线程启动、取连接、处理和核验 |
| `verified_ids_s` | 已核验 ID 数除以测量轮用时 |
| `completed_sql_s` | SQL 次数除以测量轮用时 |
| `group_p95_ms` | 以相同的 ID 组为单位，按最近秩计算 SQL 执行和客户端取结果的 P95 |

例如请求 `1,001` 个 ID、每组 `25` 个，预期两种方式均核验 `1,001` 个 ID。逐条方式执行 `1,001` 条 SQL，数组批查执行 `41` 条 SQL，包含最后一个只有 1 个 ID 的组。这是计数口径验证，实际延迟需要查看本机结果。

在 Windows 上使用本机 PostgreSQL 16、`1,000` 条合成菜谱和 `4` 个客户端线程跑了上述负载的 `3` 轮。两种模式每轮均核验 `1,001` 个 ID，实际成功的 SQL 次数分别为 `1,001` 和 `41`。同一轮验收还检查了 13 个文件导入、最后一个文件外键出错后的回滚、重复导入拒绝以及查询接口。合成主键读负载的结果适用于这次实验。

需要研究 SQL 执行过程时，可对当前数据执行以下只读查询，看索引路径、实际行数和缓冲区统计。对照实验中的 `wall_ms` 还含客户端和连接开销。 [基准实现](../src/main/java/io/recipelab/LookupBenchmark.java) · [PostgreSQL EXPLAIN 文档](https://www.postgresql.org/docs/16/using-explain.html)

```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT recipeid FROM recipe_lab.recipes WHERE recipeid = ANY (ARRAY[1, 2, 3]);
```
