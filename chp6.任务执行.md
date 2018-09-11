任务执行
---
>大多数并发应用程序都是围绕`任务执行（Task Execution）`来构造的：
任务通常是抽象的且离散的工作单元，通过把应用程序的工作分解到多个任务中，可以`简化程序的组织结构`，
提供一种`自然的事务边界`来`优化错误的恢复过程`，
提供一种`自然的并行工作结构`来提升`并发性`；
>
<!-- TOC -->

- [在线程中执行任务](#在线程中执行任务)
    - [串行的执行任务](#串行的执行任务)
    - [显式的为任务创建线程](#显式的为任务创建线程)
    - [无限制创建线程的不足](#无限制创建线程的不足)
- [Executor框架](#executor框架)
    - [示例：基于Executor的web服务器](#示例基于executor的web服务器)
    - [执行策略](#执行策略)
    - [线程池](#线程池)
    - [Executor的生命周期](#executor的生命周期)
    - [延迟任务与周期任务](#延迟任务与周期任务)
- [找出可利用的并行性](#找出可利用的并行性)
    - [示例：串行的页面渲染器](#示例串行的页面渲染器)
    - [携带结果的任务Callable与Future](#携带结果的任务callable与future)
    - [示例：使用Future实现页面渲染器](#示例使用future实现页面渲染器)
    - [在异构任务并行化中存在的局限](#在异构任务并行化中存在的局限)
    - [CompletionService:Executor与BlockingQueue](#completionserviceexecutor与blockingqueue)
    - [示例：使用CompletionService实现页面渲染器](#示例使用completionservice实现页面渲染器)
    - [示例：旅行预订门户网站](#示例旅行预订门户网站)

<!-- /TOC -->

# 在线程中执行任务
## 串行的执行任务
## 显式的为任务创建线程
## 无限制创建线程的不足

# Executor框架
## 示例：基于Executor的web服务器
## 执行策略
## 线程池
## Executor的生命周期
## 延迟任务与周期任务

# 找出可利用的并行性
## 示例：串行的页面渲染器
## 携带结果的任务Callable与Future
## 示例：使用Future实现页面渲染器
## 在异构任务并行化中存在的局限
## CompletionService:Executor与BlockingQueue
## 示例：使用CompletionService实现页面渲染器
## 示例：旅行预订门户网站