## 成员与分工
**12410724 谢宏波：**
- E-R图绘制

**SID 胡雨承：**


## Task 1: E-R Diagram
本小组使用**drawio**绘制工具，绘制本小组的E-R图，其截图如下：
![Task 1 的 ER 图](https://github.com/Kranceeee/CS307_Principles_of_Database_System_Project1/blob/main/image/ER.png?raw=true)

## Task 2: Database Design

## Task 3: Database Import

## Task 4: Compare DBMS with File I/O
### 4.1 环境规格
- **硬件规格**：
CPU: 13th Gen Intel(R) Core(TM) i7-13650HX (2.60 GHz)
RAM: 16.0 GB
系统类型: 64 位操作系统, 基于 x64 的处理器
操作系统(OS): Windows 11 Home
硬盘类型和容量: NVMe SSD/1 TB

- **软件规格**：
数据库客户端： DataGrip 2025.2.2
DBMS 服务器: PostgreSQL 17.6
关键库: PostgreSQL JDBC Driver 42.7.8
编程语言与执行环境: Java Development Kit (JDK) 22.0.2
操作系统(OS): Windows 11 Home


### 4.2 数据组织与存取规格
该任务的目标是比较DBMS与原始文件I/O在执行标准CRUD作时的性能差异。为确保比较的公平性与科学性，我们对测试数据的组织方式、存取方法以及测试语句的生成进行了严格的规格化。

- **原始数据格式与档案 I/O 结构**
测试数据基于课程提供的和其他 CSV 文件。 所有文件均采用 CSV(Comma-Separated Values)格式, 例如```recipes.csv```
	- 数据记录按行分隔
	- 每个记录中的字段使用逗号作为分隔符
	- 核心数据集包含超过50w条食谱记录

在文件 I/O 性能测试中，我们的 Java 程序（通过```CSVDcomposer.java```等类别实现）会将 CSV 文件读取到 JVM 内存的高效数据结构中（例如```List``` 或 ```HashMap```）。 所有的文件I/O模拟CRUD作均直接在这些内存数据结构上进行，以模拟应用程序直接作文件时的最佳性能。

- **DBMS 端的数据组织与结构**
	- **数据库结构**
数据库采用 PostgreSQL 17.6 服务器，并严格遵循 Task 2 的设计要求，将原始数据正规化为多个关系表格。
		- 所有表格均定义了主键和外键，以确保数据的唯一性和关系完整性。
		- 数据库结构已根据 Task 2 的三个正规形式要求进行优化。

	- **数据集**
所有原始数据已通过Task 3的高效脚本完整导入DBMS，确保数据库中的记录数量与原始档案中的记录数量一致。

### 4.3 测试程序与 SQL 描述
- **测试SQL语句的动态生成**
为确保性能比较的公正性，代码中的CRUD操作语句是通过 Java JDBC 接口动态生成的。
	- 测试数据点的选择:
		- SELECT/UPDATE/DELETE操作：代码会事先从数据库中随机选取一组数量固定的 现有主键值作为测试参数。这确保了每次测试都是对真实、已索引数据的有效操作。
		- INSERT操作：代码会生成一组数量固定的全新、不冲突的测试记录数据。

- **SQL语句的生成方式：**
所有 SQL 语句均使用 JDBC Prepared Statements 配合随机选择的参数执行，以确保数据库能够高效地编译和执行语句，同时避免 SQL 注入风险。

| 操作类型 | 语句生成目的 (测试重点) | 语句示例（项目相关） |
| :---: | :--- | :--- |
| **查询 (SELECT)** | 测试基于**食谱 ID (Primary Key)** 的高效单行检索性能。 | `SELECT * FROM Recipes WHERE Recipe_ID = ?` |
| **插入 (INSERT)** | 测试数据库处理事务、锁定和**维护索引**的开销（例如插入一条新评论）。 | `INSERT INTO Reviews (Review_ID, Recipe_ID, User_ID, Rating) VALUES (?, ?, ?, ?)` |
| **更新 (UPDATE)** | 测试数据库定位记录和**修改非索引列**的开销（例如修改评论文本）。 | `UPDATE Reviews SET Review_Text = ? WHERE Review_ID = ?` |
| **删除 (DELETE)** | 测试数据库**维护 B-tree 索引**的开销，并保持数据集规模稳定（例如删除一条测试评论）。 | `DELETE FROM Reviews WHERE Review_ID = ?` |

- **测试源码描述**

	为了执行 Task 4 的性能比较实验，我们设计并实现了一个基于 Java（JDK 22.0.2）的测试程序。该程序遵循模块化和关注点分离的原则，将数据库操作、文件 I/O 操作和测试协调逻辑分离。

	代码（作为附件提交）主要由以下四个核心类别（或功能模块）组成：



- **1. PerformanceTester.java**

- **职责：** 这是程序的主入口点和协调器，包含了 `main` 方法，负责控制整个测试流程的执行顺序。

- **测试流程与计时机制：**

- **预热 (Warm-up)：** 程序会首先执行几轮预热测试，以确保 JVM 的实时编译器（JIT）和 PostgreSQL 的缓存（Buffers）均已启动，从而避免“冷启动”对测试结果的干扰。

- **循环测试：** 在预热后，程序会进入一个主循环，迭代执行 N 次（例如 1,000 次）CRUD 操作。

- **计时：** 该模块使用 Java 中最高精度的计时 API **`System.nanoTime()`**，在每次 CRUD 操作前后获取时间戳，精确计算单次操作的**纯运行时间**。

- **结果收集：** 负责收集每次迭代的耗时数据，并在测试结束后计算平均值，用于 Task 4.4 的图表生成。



- **2. PostgreSQLOperations.java 与 PostgreSQLConnector.java**

- **职责：** 该模块封装了所有与 PostgreSQL 17.6 服务器交互的逻辑，专门执行 DBMS 端的 CRUD 操作。

- **连接管理：** 内部依赖 **`PostgreSQLConnector.java`** 模块，该模块利用 **PostgreSQL JDBC Driver 42.7.8** 建立和管理数据库连接。

- **语句执行：** 严格使用 **JDBC Prepared Statements** 来执行所有 SQL 语句（例如 `SELECT * FROM Recipes WHERE Recipe_ID = ?`）。



- **3. 文件 I/O 处理器 (CSVDcomposer.java)**

- **职责：** 该模块负责模拟原始文件 I/O 的 CRUD 操作，作为 DBMS 的对照组。 - **数据加载：** 在测试开始前，程序会将 `recipes.csv` 文件的全部内容（超过 50 万条记录）一次性读取到 JVM 内存中。 - **内存结构：** 数据被存储在高效的内存数据结构中（例如 `HashMap<Integer, Recipe>`），其中**主键（Recipe_ID）**作为 **Map** 的键。 - **CRUD 模拟：** - **SELECT (查询)：** 模拟为 `HashMap.get(key)` 操作，其时间复杂度为 O(1)。 - **INSERT/DELETE (增删)：** 模拟为 `HashMap.put(key, value)` 或 `HashMap.remove(key)` 操作。 - **核心设计：** 这种设计旨在模拟应用程序直接操作内存数据时的**最佳性能**。 - **4. 数据模型类 (Recipe.java / Review.java)** - **职责：** 这些是简单的 POJO (Plain Old Java Object) 类别，作为数据的载体，确保数据传输的一致性。 - **功能：** **`Recipe.java`** 类别定义了 `recipes` 表中一条记录的数据结构；**`Review.java`** 类别则用于处理与评论相关的 CRUD 操作。



