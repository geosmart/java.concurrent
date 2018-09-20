取消与关闭
---
>任务和线程的启动很容易，但是如何`安全、快速、可靠`的`停止`下来？
>生命周期结束（`End-of-LifeCycle`）的问题会使任务、服务以及程序的设计和实现等过程变得复杂，而这个在程序设计中非常重要的要素却经常被忽略。
>一个在行为良好的软件与勉强运行的软件之家的最主要的区别就是，行为良好的软件能很完善的处理失败、关闭和取消等过程；
>本章将给如各种实现`取消`和`中断`的机制，以及如何编写任务和服务，使他们能对取消请求做出响应；
<!-- TOC -->

- [任务取消](#任务取消)
    - [中断](#中断)
    - [中断策略](#中断策略)
    - [响应中断](#响应中断)
    - [示例：计时运行](#示例计时运行)
    - [通过Future来实现取消](#通过future来实现取消)
    - [处理不可中断的阻塞](#处理不可中断的阻塞)
    - [采用newTaskFor来封装非标准的取消](#采用newtaskfor来封装非标准的取消)
- [停止基于线程的服务](#停止基于线程的服务)
    - [示例：日志服务](#示例日志服务)
    - [关闭ExecutorService](#关闭executorservice)
    - [`毒丸`对象](#毒丸对象)
    - [示例：只执行一次的服务](#示例只执行一次的服务)
    - [shutdownNow的局限性](#shutdownnow的局限性)
- [处理非正常的线程终止](#处理非正常的线程终止)
- [JVM关闭](#jvm关闭)
    - [关闭钩子](#关闭钩子)
    - [守护线程](#守护线程)
    - [终结器](#终结器)
- [小结](#小结)

<!-- /TOC -->
# 任务取消
1. 如果外部代码能在某个操作正常完成之前将其置入`完成`状态，那么这个操作就可以称为可取消的（Cancellable）。
2. 取消某个操作的原因很多：
    * `用户请求取消`：API/GUI主动取消；
    * `有时间限制的操作`：超时取消操作；
    * `应用程序事件`：并行搜索问题，1个任务找到结果，其他任务取消；
    * `错误`：爬虫搜索时，某个任务检测到磁盘满，所有任务都将取消，并记录状态，以便稍后重启恢复；
    * `关闭`：程序关闭时，必须对正在处理或等待处理的任务执行某种操作，在`平缓的关闭过程`中，当前正在执行的任务将继续执行直到完成，而在`关闭过程`中，当前的任务则可能取消；
3. Java中没有一种安全的`抢占式方法`来`停止线程`，因此也就没有安全的抢占式方法来`停止任务`，只有一些`协作式机制`，使请求取消的任务和代码都遵循一种协商好的协议；
4. 其中一种协作机制能设置某个`已请求取消（Cancellation Requeste）`标志，而任务将定期的查看该标志。如果设置该标志，那么任务将提前结束；
5. 一个可取消任务必须拥有取消策略（Cancellation Policy）,在这个策略中将详细的定义取消操作的How、When和What
    * 其他代码如何（How）请求取消该任务；
    * 任务在何时（When）检查是否已经请求了取消；
    * 在响应取消请求时应执行哪些（What）操作；
## 中断
1. 不可靠的取消操作将把生产者置于阻塞操作中，任务将无法取消；
2. 每个线程都有一个boolean类型的中断状态。当中断线程时，这个线程的中断状态将被设置为ture;
```java
public class Thread{
    //中断目标线程
    public void interrupt(){}
    //返回目标线程的中断状态
    public boolean isInterrupted(){}
    //清除当前线程的中断状态,并返回它之前的值，这也是清除中断状态的唯一方法
    public static boolean interrupted(){}
}
```
3. 阻塞库方法，如`Thread.sleep`和`Object.wait`等，都会检查线程何时中断，并且在发现中断时提前返回，它们在响应中断时执行的操作包括：`清除中断状态`，`抛出InterruptedException`，表示阻塞操作由于中断而提前结束；
4. 调用interrupt并不意味着立即停止目标线程正在进行的工作，而只是传递了请求中断的消息，由线程在下一个合适的时刻（取消点）中断自己；
5. ` wait,sleep,join`等方法将严格的处理中断请求；当它们收到中断请求或者在开始执行时发现某个已被设置好的中断状态时，将抛出一个异常；
6. 在使用静态的interuppted方法时应该小心，因为它会清除当前线程的中断状态。如果interrupted调用返回了true，那么除非你想屏蔽这个中断，否则必须对它进行处理，可以抛出interruptedException或者通过再次调用interrupted来恢复中断状态；
7. 通常，`中断是实现取消的最合理方式`；
```java
class PrimeProducer extends Thread{
    private final BlockingQueue<BigInteger> queue;

    PrimeProducer(BlockingQueue<BigInteger> queue){
        this.queue=queue;
    }

    public void run(){
        try {
            BigInteger p=BigInteger.one;
            while(!Thread.currentThread().isInterrupted()){
                queu.put(p=p.nextProbablePrime());
            }
        } catch (InterruptedException consumed) {
            //允许线程退出
        }
    }

    public void cancel(){
        interrupt();
    }
}
```

## 中断策略
## 响应中断
## 示例：计时运行
## 通过Future来实现取消
## 处理不可中断的阻塞
## 采用newTaskFor来封装非标准的取消

# 停止基于线程的服务
## 示例：日志服务
## 关闭ExecutorService
## `毒丸`对象
## 示例：只执行一次的服务
## shutdownNow的局限性

# 处理非正常的线程终止
# JVM关闭
## 关闭钩子
## 守护线程
## 终结器

# 小结
