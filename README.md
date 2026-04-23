# Gitlet 设计文档

**Name**: LiuShengxi

---

## 1. 项目概述

**目标**：实现一个简化版 Git 版本控制系统，支持本地仓库的核心功能（提交历史、分支管理、三路合并），并额外实现远程仓库操作。

**实现范围**：

- ✅ 支持：初始化、暂存、提交、分支、合并、历史查询、文件恢复
- ✅ 支持：远程仓库（add-remote / rm-remote / push / fetch / pull）
- ❌ 不支持：变基（rebase）、子模块、Git LFS、.gitignore

**工程指标**：

- 代码量：1000+ 行 Java 代码（不含测试）
- 核心类：6 个（Main, Repository, Commit, Blob, Stage, Utils）
- 支持命令：20 个完整实现的 Gitlet 命令

---

## 2. 系统架构

### 2.1 核心实体及职责

#### **Main** — 命令路由与参数校验

- **职责**：从命令行接收 arguments，依据命令调度 Repository 中的静态方法，并对参数数量和格式进行前置校验
- **设计特点**：将命令分发与业务逻辑彻底分离，Main 只负责"解析输入、校验参数、调用对应方法"

#### **Repository** — 命令调度与状态管理中心

- **职责**：所有 Gitlet 命令的唯一业务入口，负责仓库状态管理、持久化读写、复杂算法实现
- **关键逻辑**：
  - 维护 `curBranch`、`headCommit`、`stage` 三个静态缓存变量
  - 每个命令执行前统一调用 `loadMetadata()`，从磁盘加载当前状态
  - 封装对象存储的全部细节（SHA-1 计算、分片目录、序列化读写）

**设计决策**：

- 为什么用 `static` 变量而非单例对象？Gitlet 是 CLI 程序，每次 JVM 启动执行一条命令即退出，静态变量在该生命周期内天然全局可用，且避免了单例模式的样板代码。

#### **Commit** — 不可变提交对象

- **职责**：表示文件系统某一时刻的快照，通过 `parents` 构成 DAG（有向无环图）结构
- **核心字段**：
  - `message`: 提交消息（`final String`）
  - `timestamp`: 时间戳，`initial commit` 为 `new Date(0)`，其余为当前时间（`final Date`）
  - `parents`: `LinkedList<String>`，存储父提交的 SHA-1 哈希，支持合并提交的多父场景
  - `blobId`: `TreeMap<String, String>`，文件名到 Blob SHA-1 的有序映射

**设计决策**：

- 为什么用 `LinkedList<String>` 而非固定两个 parent 字段？合并提交可能有两个父提交，使用列表结构更通用，也为未来扩展（如 Octopus Merge）预留空间。
- 为什么用 `TreeMap` 而非 `HashMap`？保证文件名有序存储，在 `status`、`log` 等需要按字典序列出文件的场景下无需额外排序。
- 所有字段均为 `final`，Commit 一旦创建不可变，避免意外修改导致哈希不一致。

#### **Blob** — 文件内容存储单元

- **职责**：通过 SHA-1 哈希实现内容寻址存储，相同内容的文件共享同一 Blob 对象
- **核心字段**：
  - `content`: `byte[]`，文件原始字节内容（`final`）

**设计优势**：

- 去重存储：修改同一文件 100 次，若内容相同则仅存 1 个 Blob
- 对二进制文件友好：直接存储 `byte[]`，不强制转 String，避免编码问题

#### **Stage** — 暂存区与删除区

- **职责**：记录 `add` 和 `rm` 操作的待提交变更
- **核心字段**：
  - `added`: `TreeMap<String, String>`，暂存待添加的文件（文件名 → Blob SHA-1）
  - `removed`: `TreeSet<String>`，暂存待删除的文件名

**设计决策**：

- 使用两个独立容器分离"添加"和"删除"语义，比用一个 Map 加状态标记更清晰
- 均选用 `Tree` 系列集合，保证迭代顺序稳定，与 Commit 的 `TreeMap` 风格一致

---

### 2.2 实体关系

```
Commit Graph (DAG):
  c0 ←─ c1 ←─ c2 ←─ c4 (master)
         ↖         ↗
           c3 (feature)
```

- **Commit → Commit**：通过 `parents` 列表构成 DAG
- **Commit → Blob**：通过 `blobId` 映射，间接引用文件内容
- **Branch → Commit**：分支文件内容为 Commit SHA-1
- **HEAD → Branch**：HEAD 文件直接存储当前分支名称（如 `master`）

---

## 3. 存储设计

### 3.1 目录结构

```
.gitlet/
├── HEAD                    # 当前分支名称（如 master）
├── stage                   # 序列化的 Stage 对象
├── remote                  # 序列化的远程仓库配置 TreeMap<remoteName, path>
├── objects/                # 统一对象存储（Commit + Blob）
│   ├── a1/                 # SHA-1 前 2 位作为子目录（与 Git 一致）
│   │   └── 23c4f...        # 对象文件（SHA-1 剩余 38 位）
│   └── b2/
│       └── 8d9a1...
└── refs/
    ├── heads/              # 本地分支指针目录
    │   ├── master          # master 分支（内容：Commit SHA-1）
    │   └── feature         # feature 分支
    └── remotes/            # 远程分支镜像目录
        ├── origin
        │   └── master      # fetch 后的远程分支引用
```

**设计对齐**：

- 与真实 Git 的 `.git/objects` 统一存储策略一致，不区分 commit 和 blob 的物理路径，全部通过 SHA-1 寻址
- 使用 SHA-1 前 2 位分片，避免单目录文件过多导致文件系统性能下降
- 远程分支镜像隔离在 `refs/remotes/` 下，与本地分支严格区分，防止命名冲突

---

### 3.2 序列化策略

| 对象类型 | 序列化方式 | 原因 |
|---------|-----------|------|
| Commit | Java Serializable | 包含复杂嵌套结构（TreeMap、LinkedList、Date），标准序列化最简单 |
| Blob | Java Serializable | 虽仅含 byte[]，但统一用 `writeObject` 保持接口一致 |
| Stage | Java Serializable | TreeMap + TreeSet 需要保留键值关系和有序性 |
| Branch / HEAD / Remote | 纯文本 | 仅存储字符串，文本文件便于调试和手动查看 |

**关键实现**：

- `generateSerObjHash(Serializable obj)` 对 Blob 做特殊处理：Blob 的哈希仅基于其 `content` 计算，而非序列化后的字节流。这确保了**相同内容的 Blob 哈希绝对一致**，避免序列化头部信息引入不确定性。

---

## 4. 核心算法

### 4.1 暂存区管理算法

#### **add 命令流程**

```java
1. 计算文件内容的 SHA-1 → shaFile
2. 读取当前 HEAD Commit → headCommit
3. 比较：
   - 若 headCommit.blobId.get(文件名) == shaFile
     → 文件与 HEAD 版本一致，从 stage.added 移除（若存在），同时从 stage.removed 移除
   - 否则：
     → 将文件内容封装为 Blob，写入 objects/<sha前2位>/<sha剩余>
     → stage.added.put(文件名, shaFile)
     → stage.removed.remove(文件名)
4. 序列化 stage 至磁盘
```

**关键优化**：

- 若文件恢复到 HEAD 版本，自动从暂存区移除（与真实 Git 行为一致）
- `add` 会清除该文件在 `removed` 区的标记，支持"删了后悔"的场景

---

### 4.2 三路合并算法

#### **核心思路**

合并两个分支时，找到它们的"分割点"（split point），即最近公共祖先（LCA, Lowest Common Ancestor）。基于分割点、当前分支、目标分支的三方文件状态，按规则决定合并结果。

#### **步骤 1: 查找分割点（带距离优化的 BFS）**

```java
getSplitPoint(headCommitSha, otherCommitSha):
    1. distances1 = BFS(headCommitSha) → Map<CommitSha, 拓扑距离>
    2. distances2 = BFS(otherCommitSha) → Map<CommitSha, 拓扑距离>
    3. 遍历 distances1 和 distances2 的交集：
         找拓扑距离最小的共同祖先
         若距离相同，取 SHA-1 字典序较小者（确定性规则）
    4. 返回该 Commit
```

**算法复杂度**：

- 时间：$O(C_1 + C_2)$，其中 $C_1, C_2$ 为两分支的提交数
- 空间：$O(C_1 + C_2)$（存储两个距离映射）

**设计优势**：

- 相比单次 BFS 找第一个共同祖先，计算双方到各祖先的**距离**并取最小值，确保找到的是拓扑上"最近"的分割点，避免误判。
- 距离相同时用 SHA-1 字典序打破平局，保证结果完全确定、可复现。

---

#### **步骤 2: 三路文件比较与处理**

对三个 Commit（split、head、other）中出现的所有文件，根据三方状态决定操作：

由 `processFile` 方法系统处理以下场景：

| Split | Head | Other | 操作 |
|-------|------|-------|------|
| A | A | A | 不变 |
| A | B | A | 保持 Head（B） |
| A | A | C | 检出 Other（C），并 add |
| A | B | C | **冲突**（双方均改且不同） |
| null | null | C | 检出 Other（C），并 add |
| A | null | A | 删除文件（rm） |
| A | null | C | **冲突** |
| A | B | null | 保持 Head（B） |

**关键规则**：

- 若某一方未修改（与 Split 相同），采用另一方
- 若双方都修改且不同，标记冲突
- `processFile` 返回 `boolean` 表示是否发生冲突，由上层统计并输出提示

---

#### **步骤 3: 冲突处理**

当检测到冲突时，生成冲突标记文件：

```java
<<<<<<< HEAD
Current Branch 的文件内容
=======
Target Branch 的文件内容
>>>>>>>
```

**实现细节**：

- 若某一方文件不存在，对应部分留空字符串
- 冲突文件写入工作目录后自动 `add`，确保冲突状态被暂存
- 合并提交的 `parents` 包含两个元素：当前分支 HEAD + 目标分支 HEAD

---

### 4.3 远程对象同步算法（DFS + 栈）

`syncObjects(sourceDir, targetDir, startSha, endSha)` 用于 push/fetch 时批量复制对象：

```java
1. 初始化栈，压入 startSha
2. while 栈非空：
     pop 一个 sha
     - 若 sha == endSha 或已访问：跳过
     - 标记已访问
     - 从 sourceDir 读取对象，写入 targetDir（若 target 中不存在）
     - 若该对象是 Commit：
         → 将其所有 blobSha 和 parentSha 压入栈
3. 结束
```

**设计决策**：

- 使用显式栈（`ArrayDeque`）实现 DFS，避免递归深度过大导致栈溢出
- `endSha` 作为终止条件，避免复制远程已存在的对象，减少 I/O
- 同时遍历 parent（提交链）和 blob（文件内容），确保复制一棵完整的"对象子图"

---

## 5. 命令实现细节

### 5.1 核心命令

| 命令 | 功能 | 关键实现 |
|------|------|---------|
| `init` | 初始化仓库 | 创建 `.gitlet/` 目录结构，生成初始 Commit（时间戳为 Unix Epoch 0），创建 master 分支和 HEAD |
| `add <file>` | 添加到暂存区 | 计算 SHA-1，比较 HEAD 版本，必要时写入 Blob，更新 stage.added |
| `commit <msg>` | 创建提交 | 基于 HEAD Commit 的 blobId，合并 stage.added，剔除 stage.removed，生成新 Commit，更新分支指针 |
| `rm <file>` | 移除文件 | 从 stage.added 删除，或加入 stage.removed 并删除工作目录文件 |
| `log` | 显示当前分支历史 | 从 HEAD 开始，沿 `parents.get(0)` 单向遍历，按时间倒序输出 |
| `global-log` | 显示所有提交 | 遍历 `objects/` 目录下所有对象，筛选出 Commit 类型输出 |
| `find <msg>` | 按消息查找提交 | 遍历所有 Commit 对象，匹配 message 字段 |
| `status` | 显示仓库状态 | 列出分支、暂存区、已删除、未暂存修改、未追踪文件 |
| `checkout` | 恢复文件/切换分支 | 三种模式：从 HEAD 恢复、从历史 Commit 恢复、切换分支（含 untracked 文件保护） |
| `branch <name>` | 创建分支 | 在 `.gitlet/refs/heads/` 创建文件，内容为当前 HEAD Commit SHA-1 |
| `rm-branch <name>` | 删除分支 | 删除分支文件，禁止删除当前分支 |
| `reset <commit>` | 回退到指定提交 | 更新当前分支指针，检出 Commit 的所有文件，清空 stage |
| `merge <branch>` | 合并分支 | 执行三路合并算法，生成合并提交（parents 含两个元素） |
| `add-remote <name> <path>` | 添加远程仓库 | 序列化 TreeMap 至 `.gitlet/refs/remote` |
| `rm-remote <name>` | 移除远程仓库 | 从 TreeMap 中删除对应条目 |
| `push <remote> <branch>` | 推送到远程 | 检查远程 HEAD 是否为本地 HEAD 的祖先，同步对象并更新远程分支指针 |
| `fetch <remote> <branch>` | 从远程拉取对象 | 将远程对象同步到本地 `objects/`，更新 `refs/remotes/<remote>/<branch>` |
| `pull <remote> <branch>` | 拉取并合并 | 先 `fetch`，再 `merge` 对应的远程分支 |

---

### 5.2 边界情况处理

#### **未初始化仓库**

- 所有命令（除 `init`）需先检查 `.gitlet/` 目录是否存在
- 若不存在，输出 `Not in an initialized Gitlet directory.` 并退出

#### **重复 `init`**

- 检测到 `.gitlet/` 已存在，抛出异常，避免覆盖现有仓库

#### **`add` 不存在的文件**

- 调用 `File.exists()`，若返回 false，抛出 `File does not exist.`

#### **`commit` 无变更**

- 检查 stage 是否为空（added 和 removed 均为空），若是，拒绝提交并提示 `No changes added to the commit.`

#### **合并冲突**

- 冲突文件写入冲突标记后自动 `add`，确保用户解决冲突后可直接 `commit`
- 合并提交时若存在冲突，输出 `Encountered a merge conflict.`

---

## 6. 设计特点总结

### 6.1 统一对象存储

Commit 和 Blob 共享同一个 `objects/` 目录，不区分物理位置，完全依靠 SHA-1 寻址。这种设计与真实 Git 的 object database 一致，简化了存储管理逻辑。

### 6.2 状态加载前置化

`loadMetadata()` 在每个命令入口统一执行，将 HEAD、当前 Commit、Stage 加载到静态缓存中。后续业务逻辑全部操作内存对象，减少磁盘 I/O 的分散调用，使代码结构更清晰。

### 6.3 哈希计算差异化

`generateSerObjHash` 对 Blob 做特殊处理：Blob 的哈希仅基于原始 `content`，而非 Java 序列化后的字节流。这消除了序列化实现细节对哈希值的干扰，保证内容相同的 Blob 哈希绝对一致，是实现内容寻址存储的关键。

### 6.4 远程与本地隔离

远程分支镜像存储在 `refs/remotes/<remote>/<branch>`，与本地分支 `refs/heads/<branch>` 严格隔离。`getBranchFile` 方法通过解析 `"origin/master"` 格式的名称自动路由到正确位置，支持本地和远程分支的统一操作接口。

---

## 7. 已知限制与未来改进

### 7.1 当前限制

- ❌ 不支持 `.gitignore`（需解析规则并过滤文件）
- ❌ 不支持 `git rebase`（需重写 Commit 历史）
- ❌ 不支持文件权限和符号链接（仅追踪普通文件内容）
- ❌ 暂存区与分支操作非线程安全（CLI 单进程场景下可接受）

### 7.2 可能的优化方向

1. **性能优化**：
   - 引入对象缓存（如 LRU Cache），避免重复反序列化高频访问的 Commit
   - 大文件可采用引用存储（类似 Git LFS），避免 objects 目录膨胀

2. **功能扩展**：
   - 实现 `git stash`（临时保存未提交变更）
   - 支持交互式暂存（`git add -p`）

3. **代码质量**：
   - 引入 Command 模式统一命令接口，减少 Repository 类的体积
   - 将远程操作抽离为 RemoteManager 独立类，降低 Repository 复杂度

---

## 8. 参考资料

- [Git Internals - Plumbing and Porcelain](https://git-scm.com/book/en/v2/Git-Internals-Plumbing-and-Porcelain)
- [CS61B Spring 2024 Project 2 Spec](https://sp24.datastructur.es/)
- [CS61B结课笔记，感想，以及资源](https://www.bilibili.com/read/cv18985812/?from=readlist&opus_fallback=1)
- [CS61B Gitlet项目的指南与反思](https://zhuanlan.zhihu.com/p/1970659158467523477)
- [CS61B Gitlet入坑指南](https://zhuanlan.zhihu.com/p/533852291)
- [Gitlet 设计文档](https://github.com/m0NESY0501/yyhcs61b_showcase/blob/main/proj2gitlet%E8%AE%BE%E8%AE%A1%E6%96%87%E6%A1%A3.md)

---

**最终总结**：本项目完整实现了 Git 的核心数据结构和算法，包括 DAG 提交图、内容寻址存储、三路合并等关键技术，并在标准 CS61B 要求之外扩展了完整的远程仓库操作（push/fetch/pull）。代码通过统一对象存储、前置状态加载、确定性分割点查找等设计，在保证功能正确性的同时兼顾了可维护性。
