# Recipe Data Lab · 菜谱数据工程平台

基于 Java 与 PostgreSQL 的菜谱数据导入、查询与性能实验项目。它将作者、菜谱、食材、分类、营养信息和评价建模为 13 张关系表。可用原有规范化 CSV 或一键生成的合成数据复现导入和查询。

| 能力 | 实现 |
|---|---|
| 批量导入 | JDBC `CopyManager` + PostgreSQL `COPY FROM STDIN`，核对 CSV 表头、按外键依赖顺序导入，数据库事务覆盖全部 13 张表 |
| 菜谱检索 | Spring Boot API 支持名称片段、食材、分类、总耗时组合筛选，以及按菜谱 ID 的游标分页 |
| 关联查询 | 菜谱详情聚合营养、食材、标签和分类，独立接口读取评价 |
| 可重跑的性能实验 | 同一批已知菜谱 ID 对照逐条查询与数组批查，校验返回结果，分别记录完成的 SQL、已核验 ID、耗时与 P95 |

## 快速启动

依赖 JDK 17+、Maven、PostgreSQL 16 和 Python 3.10+。Python 用来生成测试数据及运行接口检查。可以用现有 PostgreSQL，也可以通过 Docker Compose 启动一个本机实例。

在仓库根目录打开 PowerShell，为本地数据库设置一个密码并启动数据库。

```powershell
$env:RECIPE_DB_PASSWORD = '<替换为本机使用的密码>'
docker compose up -d --wait db
mvn -B package
python scripts/demo_data.py data/demo --recipes 1000
java -jar target/recipe-data-lab-1.0.0.jar import data/demo
java -jar target/recipe-data-lab-1.0.0.jar
```

在另一个终端运行 `python scripts/smoke.py`，或用浏览器打开以下地址。

- [浏览菜谱](http://127.0.0.1:8080/api/recipes)
- [查看菜谱 1 的详情](http://127.0.0.1:8080/api/recipes/1)
- [查看菜谱 1 的评价](http://127.0.0.1:8080/api/recipes/1/reviews)

在 macOS 或 Linux 上使用 `export RECIPE_DB_PASSWORD='你的本地密码'` 设置变量，其余命令可用同样的参数。

若使用现有 PostgreSQL，创建一个数据库，并设置 `RECIPE_DB_URL`、`RECIPE_DB_USER` 和 `RECIPE_DB_PASSWORD`。应用会创建并使用 `recipe_lab` schema，连接用户需要该库的建 schema 权限或对预建 schema 的写入权限。

| 配置 | 默认值 |
|---|---|
| `RECIPE_DB_URL` | `jdbc:postgresql://127.0.0.1:55438/recipes` |
| `RECIPE_DB_USER` | `recipe_lab` |
| `RECIPE_DB_PASSWORD` | 本地设置 |
| `RECIPE_DB_POOL_SIZE` | `8` |
| `RECIPE_BIND_ADDRESS` | `127.0.0.1` |
| `RECIPE_PORT` | `8080` |

## 数据导入

导入命令接受一个含 13 个 UTF-8 CSV 的目录。支持原有规范化文件名和列序，例如 `User.csv`、`Recipe.csv`、`Recipe_Ingredient.csv`。从原始文件拆分 CSV 的程序保存在 [`CSProject`](CSProject) 中。新入口的[完整文件与字段约定](docs/data-format.md)可用于检查输入。

导入前检查文件是否齐全，读取每个文件时验证表头，然后流式执行 `COPY`。主键、外键、格式或列约束失败会回滚整个导入，进程返回非零状态并报告失败文件。成功提交后才打印各表实际加载行数。并发导入用事务级锁串行化，已含数据的 schema 会明确拒绝重复导入。重新实验可使用新数据库。

合成数据生成器固定菜名、食材和主外键关系，并用可调数量扩展测试数据。它适合验证导入和查询，也方便别人重复同样的负载。

## 查询接口

| 请求 | 结果 |
|---|---|
| `GET /api/health` | 验证 Web 服务与数据库连接 |
| `GET /api/recipes?q=&ingredient=&category=&maxMinutes=&after=&limit=` | 菜单查询和分页，所有过滤条件可组合使用 |
| `GET /api/recipes/{id}` | 菜谱详情、作者、食材、分类、标签和营养信息 |
| `GET /api/recipes/{id}/reviews?limit=20` | 按评价 ID 倒序返回评价 |

名称按字面子串匹配，食材和分类按完整名称匹配，均忽略大小写。`limit` 范围为 `1–100`。上一页返回的 `nextAfter` 用作下一页的 `after`。返回空列表表示筛选没有命中。非法参数返回 `400`，找不到指定菜谱返回 `404`。

```powershell
curl.exe -G 'http://127.0.0.1:8080/api/recipes' --data-urlencode 'q=番茄' --data-urlencode 'ingredient=鸡蛋' --data-urlencode 'limit=5'
```

## 性能实验

导入样例后，在配置了同一数据库连接的终端运行。

```powershell
java -jar target/recipe-data-lab-1.0.0.jar benchmark 10001 25 4 3
```

四个参数依次是查找次数、每组 ID 数、客户端线程数和轮数。两种实现使用同一组 ID、相同列与连接池，先预热并在每轮交替运行顺序。每组逐个验证返回的 ID，结果里分开统计 `verified_ids` 与 `completed_sql`，并报告每组请求耗时 P95。样例命令的每轮 `POINT` 会执行 `10,001` 条 SQL，`ARRAY` 会执行 `401` 条，两者都需核验 `10,001` 次 ID 查找。耗时和吞吐以自己的实际输出为准。

这项实验衡量 Java 客户端到 PostgreSQL 的主键查找，输出包括逻辑查找数和 JDBC 成功完成的 SQL 次数。详情见[实验方法](docs/benchmark.md)。

## 项目结构

```text
src/main/java/io/recipelab/   查询 API、流式导入、基准测试
src/main/resources/          PostgreSQL 表结构与应用配置
src/test/                     性能指标与负载边界的回归测试
scripts/                      合成数据生成与接口验收
CSProject/ · CSVImporter/    原有 CSV 拆分和导入实现
UltimateVersion/ · Report/   早期测试与分析资料
```

## 团队贡献

- 谢宏波负责 E-R 建模、数据库与文件 I/O 的比较、连接池及并发实验、查询逻辑迭代和报告。
- 胡雨承负责数据库建表、Java 数据筛选与导入、导入优化比较和报告。

以上沿用初版团队分工。早期实现及历史测试记录可从 Git 历史查看。 [初版记录](https://github.com/Kranceeee/CS307_Principles_of_Database_System_Project1/tree/a3e36e5)
