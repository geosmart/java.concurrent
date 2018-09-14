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
1. 当围绕`任务执行`来设计应用程序结构时，第一步就是要找出清晰的任务边界；
    * 在理想情况下，各个任务之间是相互独立的：任务并不依赖其他任务的状态、结果或边界效应；
    * 独立性有助于实现并发；
    * 在正常负载下，服务器应用程序应该同时表现出良好的吞吐量和快速的响应性；
    * 当负荷过载时，应用程序的性能应该是逐渐降低，而不是直接失败；
    * 大多数服务器应用程序都是提供了一种自然的任务边界选择放手：以独立的客户请求为边界；
2. 第二步就是有明确的任务执行策略；
## 串行的执行任务
1. 在应用程序中可以通过多种策略来调度任务，而其中一些策略能够更好的利用潜在的并发性；
2. 最简单的策略就是在单个线程中串行的执行各项任务；
3. 在服务器应用程序中，串行处理程序通常都无法提供高吞吐率或快速响应性；服务器的资源利用率非常低，因为当单线程在等待I/O操作完成时，CPU将处于空闲状态；
## 显式的为任务创建线程
1. 为每个请求创建一个线程来提供服务，从而实现更高的响应性；
* 任务处理过程从`主线程`中分离出来，使得主循环能够更快的重新等待下一个到来的连接，这使得程序在完成前面的请求之前可以接受新的请求，从而提高响应性；
* 任务可以`并行处理`，从而能同时服务多个请求。如果有多个服务器，或者由于某种原因被阻塞，例如`等待I/O完成`、`获取锁`或者`资源可用性`等，程序的吞吐量将得到提高；
* `任务处理代码必须是线程安全的`，因为有多个线程会并发的调用这段代码；
2. 在正常负载情况下,`为每个任务分片一个线程`的方法能提升串行执行的性能。只要`请求的到达率`不超过服务器的请求`处理能力`，那么这种方法可以同时带来更快的`响应性`和更高的`吞吐率`；
## 无限制创建线程的不足
在生产环境，为`每个任务分配一个线程`这种方法存在一些缺陷，尤其是需要创建大量线程时：
1. 线程生命周期的开销非常高：创建和销毁有时间代价；
2. 资源消耗：活跃的线程会消耗系统资源，尤其是内存；
    * 如果可运行的线程数量多于可用处理器的数量，那么有些线程将会闲置；
    * 大量空闲的线程会占用许多内存，给垃圾回收器带来压力，而且大量线程在竞争CPU资源时还将产生其他的性能开销。
    * 如果你已经拥有足够多的线程使CPU保持忙碌状态，那么再创建更多的线程反而会降低性能；
3. 稳定性：在可创建线程的数量上存在一个线程，这个限制值将随着平台的不同而不同，并且受多个因素制约，包括JVM启动参数、Thread构造函数中请求的栈的大小，以及底层操作系统对线程的限制等；
4. 安全性；某个恶意用户或者过多的用户请求将使得服务器过载，过多的创建线程导致服务崩溃；

# Executor框架
1. 任务是一组逻辑工作单元，而线程则是使任务异步执行的机制；
2. 在Java类库中，任务执行的主要抽象不是Thread，而是Executor
```java
public interface Executor{
    void execute(Runnable command);
}
```
3. Executor提供了一种标准的方法将任务的提交过程与执行过程解耦开来，并用Runnable来表示任务。
4. Executor的实现还提供了对生命周期的支持，以及统计信息收集、应用程序管理机制和性能监视机制；
5. Executor基于生产者-消费者模式，提交任务的操作相当于生产者，执行任务的操作相当于消费者；

## 示例：基于Executor的web服务器
```java
class TaskExecutionWebServer{
    private static final int NTHREAD=100;
    private static final Executor exec=Executors.newFixedThreadPool(NTHREAD);

    public static void public static void main(String[] args) throws IOException {
        ServletSocket socket=new ServerSocket(80);
        while (true) {
            final Socket connection=socket.accept();
            Runnable task=new Runnable(){
                public void run(){
                    handleRequest(connection);
                }
            }
            exec.execute(task);
        }
    }
}
```
## 执行策略
1. 通过将任务提交与任务执行解耦开来，便于为某种类型的任务指定和修改执行策略；
2. 在执行策略中定义了任务执行的`what、where、when、how`等方面，包括：
    * 在什么(what)中执行任务； 
    * 任务按照什么(what)顺序执行（FIFO、LIFO、优先级）?
    * 有多少个(how many)任务能`并发`执行?
    * 在队列中有多少(how many)个任务在`等待执行`?
    * 如果系统由于过载需要拒绝一个任务，那么应该选择哪一个(which)任务可以，另外，如何(how)通知应用程序有任务被拒绝?
    * 在执行一个任务之前或之后，应该进行哪些(what)操作?
3. 各种执行策略都是一种资源管理工具，最佳策略取决于`可用的计算资源`以及`对服务质量的需求`。
4. 通过限制`并发任务`数量，可以确保应用程序不会由于资源耗尽而失败，或由于在稀缺资源上发生`竞争而严重影响性能`；
5. 通过将任务的提交与任务的执行策略分离开来，有助于在`部署阶段`选择与可用硬件资源最匹配的`执行策略`；
## 线程池
1. 线程池，是指管理一组`同构`工作线程的资源池；
2. 线程池是与工作队列（Work Queue）密切相关的，其中在工作队列中保存了所有等待执行的任务；
3. 工作者线程（Work Thread）的任务很简单：从工作队列中获取一个任务，执行任务，然后返回线程池并等待下一个任务；
4. 在线程中执行任务比为每个任务分配一个线程有更多优势：
    * 线程重用
    * 任务到达时线程已存在，不会因为等待线程创建而延迟执行任务，提高响应速度；
    * 可以通过适当调整线程池大小，创建足够多的线程以便使处理器保持忙碌状态，同时还可以防止多线程互相竞争资源而导致程序耗尽内存或失败；
5. jdk类库中，可通过Executors中的静态工厂方法创建一个线程池：
    * newFixedThreadPool：固定长度的线程池，每当提交一个任务时就创建一个线程，直到达到线程池的最大数量；
    * newCachedThreadPool：将创建一个可缓存的线程池，线程池规模不受限制，若果线程池规模超过了处理需求时，那么将回收空闲的资源，而当需求增加时，则可以添加新的线程；
    * newSingleThreadExecutor：单线程Executor，能够确保依照任务在队里中的顺序来串行执行（FIFO、LIFO、优先级）；
    * newScheduledThreadPool：创建一个固定长度的线程池，而且以延迟或定时的方式来执行任务，类似于Timer；
6. `为每一个任务分配一个线程策略`变成了基于线程池的策略，将对应用程序的稳定性产生重大的影响：Web服务器不会再在高负载的情况下失败；
7. 由于服务器不会创建数千个线程来争夺优先的CPU和内存资源，因此服务器的性能将平缓的降低；
8. 通过Executor可以实现各种调优、管理、监视、记录日志、错误报告和其他功能；
## Executor的生命周期
1. Executor的实现通常会创建线程来执行任务。但JVM只有在所有（非守护）线程全部终止后才会退出。因此如果无法正确的关闭Executor，那么JVM将无法结束；
2. 当关闭应用程序时，尽可能采样最平缓的关闭形式（完成所有已经启动的任务，并且不再接受任何新的任务），也可采样最粗暴的关闭形式（直接关闭机房），以及各种可能的形式；
3. 为了解决执行服务的生命周期问题，Executor扩展类ExecutorService接口，添加了一些用于生命周期管理方法（同时还有一些用于任务提交的便利方法）；
```java
public interface ExecutorService extends Executor{
    void shutdown();
    List<Runnable> shutdownNow();
    boolean isShutdown();
    boolean isTerminated();
    boolean awaitTermination(long timeout,TimeUnit unit) throws InterruptedException; 
}
```
4. ExecutorService的生命周期有3种状态：`运行`、`关闭`和`已终止`；
    * ExecutorService在初始创建时处于运行状态；
    * shutdown方法将执行平缓的关闭过程：不再接受新任务，同时等待已经提交的任务完成-包括哪些还未开始执行的任务；
    * shutdownNow方法将执行粗暴的关闭过程：它将尝试取消所有运行中的任务，并且不再启动队列中尚未执行的任务；
5. 在ExecutorService关闭后提交的任务将由`拒绝执行处理器（Rejected Execution Handler）`来处理，它会抛弃任务，或者使得execute方法抛出一个未检查的RejectedExecutionException；
6. 等所有任务都完成后，ExecutorService将进入终止状态；可以调用`awaitTermination`来等待ExecutorService到达终止状态，或者通过`isTerminated`来轮询ExecutorService是否已经终止；
7. 通常在调用awaitTermination之后会立即调用shutdown，从而产生同步的关闭ExecutorService的效果；
8. 通过增加生命周期支持来扩展Web服务器功能，可以通过两种方式来关闭Web服务器：
    * 在程序中调用stop;
    * 以客户端请求形式向web服务器发生一个特定格式的http请求；
```java
class LifeCycleWebServer{
    private final ExecutorService exec=;

    public void start()throws IOException{
        ServerSocket socket=new ServerSocket(80);
        while(!exec.isTerminated){
            try {
                final Socket conn=socket.accept();
                exec.execute(new Runnable(){
                    public void run(){
                        handleRequest();
                    }
                })
            } catch (RejectedExecutionException e) {
                if(!exec.isShutdown){
                    log("task submission rejected",e);
                }
            }
        }
    }

    public void stop(){
        exec.shutdown();
    }

    void handleRequest(Socket connection){
        Request req=readRequest(connection);
        if(isShutdownRequest(req)){
            stop();
        }else{
            dispatchRequest(req);
        }
    }
}
```
## 延迟任务与周期任务
1. Timer类负责管理`延迟任务`(在100ms后执行该任务)以及`周期任务`(每10ms执行一次该任务);然而Timer存在一些缺陷:Timer支持基于`绝对时间`而不是相对时间的调度机制，因此任务的执行对系统时钟变化很敏感，而ScheduledThreadPoolExecutor支持基相对时间的调度；因此可以考虑用ScheduledThreadPoolExecutor来代替它。
2. Timer可能会出现线程泄露(Thread Leakage)，如果Timer线程抛出未检查的异常会被当做调度取消处理，新的任务不会再触发；
3. 如果要构建调度服务，建议使用`DelayQueue`，它实现了`BlockingQueue`，并为`ScheduledThreadExecutor`提供调度功能。
4. DelayQueue管理着一组`Delay`对象，每个Delay对象都有一个相应的延迟时间：在DelayQueue中，只有某个元素`逾期`后，才能从DelayQueue中执行`take`操作。从DelayQueue中返回的对象将根据它们的延迟时间进行排序；

# 找出可利用的并行性
## 示例：串行的页面渲染器
## 携带结果的任务Callable与Future
## 示例：使用Future实现页面渲染器
## 在异构任务并行化中存在的局限
## CompletionService:Executor与BlockingQueue
## 示例：使用CompletionService实现页面渲染器
## 示例：旅行预订门户网站