线程池的使用
---
<!-- TOC -->

- [在任务与执行策略之间的隐性耦合](#在任务与执行策略之间的隐性耦合)
    - [线程饥饿死锁（Thread Starvation Deallock）](#线程饥饿死锁thread-starvation-deallock)
    - [运行时间较长的任务](#运行时间较长的任务)
- [设置线程池的大小](#设置线程池的大小)
- [配置ThreadPoolExecutor](#配置threadpoolexecutor)
    - [线程的创建和销毁](#线程的创建和销毁)
    - [管理队列任务](#管理队列任务)
    - [饱和策略](#饱和策略)
    - [线程工厂](#线程工厂)
    - [在调用构造函数后再定制ThreadPoolExecutor](#在调用构造函数后再定制threadpoolexecutor)
- [扩展ThreadPoolExecutor](#扩展threadpoolexecutor)
- [递归算法的并行化](#递归算法的并行化)
- [小结](#小结)

<!-- /TOC -->

# 在任务与执行策略之间的隐性耦合
并非所有任务都能够以Executor框架将提交与执行策略解耦，有些类型的任务需要明确的指定执行策略：
1. 依赖性任务：注意活跃性问题；
2. 使用线程封闭机制的任务：任务要求其执行所在的Executor是单线程的；
3. 对响应时间敏感的任务：GUI程序；
## 线程饥饿死锁（Thread Starvation Deallock）
1. 在线程池中，如果任务以来于其他任务，那么可能产生死锁；
2. 在单线程Executor中，如果一个任务将另一个任务提交到同一个Executor，并且等待这个被提交任务的结果，那么通常会引发死锁；
3. 在更大的线程池中，如果所有正在执行的线程都由于等待其他仍处于工作队列中的任务而阻塞，那么会发生`线程饥饿死锁`；
4. 只要线程池中的任务需要无限期的等待一些必须由池中其他任务才能提供的资源或条件，例如某个任务等待另一个任务的返回值或结果，那么除非线程池足够大，否则将发生线程饥饿死锁；

>饥饿死锁示例
```java
public class ThreadDeadLock{
    ExecutorService exec=Executors.newSingleThreadPoolExecutor();
    
    public class RenderPageTask implements Callable<String>{
        public String call()throws Exception{
            Future<String> header,footer;
            header=exec.submit(new LoadFileTask("header.html"));
            footer=exec.submit(new LoadFileTask("footer.html"));
            String page =renderBody();
            //将发生死锁--由于等待子任务的结果
            return header.get()+page+footer.get();
        }
    }
}
```
每当提交一个有依赖性的Executor任务时，需要清楚的知道可能会出现线程`饥饿死锁`，因此需要在代码或配置Executor的配置文件中记录线程池的大小现在或配置限制；

## 运行时间较长的任务

# 设置线程池的大小

# 配置ThreadPoolExecutor
## 线程的创建和销毁
## 管理队列任务
## 饱和策略
## 线程工厂
## 在调用构造函数后再定制ThreadPoolExecutor

# 扩展ThreadPoolExecutor

# 递归算法的并行化

# 小结