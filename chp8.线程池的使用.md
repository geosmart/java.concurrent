线程池的使用
---
本章介绍对线程池进行配置与调优的一些高级选项，并分析在使用任务执行框架时需要注意的各种危险，以及一些使用Executor的高级示例。

<!-- TOC -->

- [在任务与执行策略之间的隐性耦合](#在任务与执行策略之间的隐性耦合)
    - [线程饥饿死锁（Thread Starvation Deallock）](#线程饥饿死锁thread-starvation-deallock)
    - [运行时间较长的任务](#运行时间较长的任务)
- [设置线程池的大小](#设置线程池的大小)
- [配置ThreadPoolExecutor](#配置threadpoolexecutor)
    - [线程的创建和销毁](#线程的创建和销毁)
    - [管理队列任务](#管理队列任务)
        - [SynchronousQueue（工作移交）](#synchronousqueue工作移交)
    - [饱和策略](#饱和策略)
    - [线程工厂](#线程工厂)
    - [在调用构造函数后再定制ThreadPoolExecutor](#在调用构造函数后再定制threadpoolexecutor)
- [扩展ThreadPoolExecutor](#扩展threadpoolexecutor)
- [递归算法的并行化](#递归算法的并行化)
- [小结](#小结)

<!-- /TOC -->

# 在任务与执行策略之间的隐性耦合
并非所有任务都能够以Executor框架将提交与执行策略解耦，有些类型的任务需要明确的指定执行策略：
1. 依赖性任务：注意`活跃性问题`；
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
1. 如果任务阻塞的时间过长，那么即使不出现死锁，线程池的响应性也会变得很糟；
2. 执行时间较长的任务不仅会造成线程池阻塞，甚至会增加执行时间较短任务的服务时间；
3. 限定任务等待资源时间可以缓解执行时间较长任务造成的影响，而不需要无限制的等待；
4. Thread.join，BlocingQueue.put，CountdownLatch.await以及Selector.select等都定义了限时版本和无限时版本；
5. 如果等待超时，那么可以将任务标识为失败，然后中止任务或将任务重新放回队列以便随后执行。
6. 如果线程池中总是充满了被阻塞的任务，那么也可能表明线程池的规模过小；

# 设置线程池的大小
1. 线程池的理想大小取决于被提交`任务的类型`以及所部署`系统的特性`；
2. 一般通过某种配置机制来设置线程池的大小，或通过`Runtime.getRuntime().availableProcessors()`来动态计算；
3. 要合理设置线程池的大小，需要避免`极大`和`过小`这两种极端情况：
    * 如果线程池过大，那么大量的线程将在相对很少的CPU和内存资源上发生竞争，这不仅会导致更高的内存使用量，而且还可能耗尽资源；
    * 如果线程池过小，那么将导致许多空闲的处理器无法执行工作，从而降低吞吐率；
4. 要想正确的设置线程池的大小，必须分析计算环境、资源预算和任务的特性。
    * 在部署的系统中有多少个CPU？多大内存？
    * 任务是`计算密集型`、`I/O密集型`还是二者皆可？
    * 他们是否需要像JDBC连接这样的稀缺资源？
5. 如果需要`执行不同类别的任务`，并且他们之间的行为相差很大，那么应该考虑使用多个线程池，从而为每个线程池可以根据各自的工作负载来调整；
6. 对于计算密集型的任务，在拥有N个CPU的系统上，当线程池的大小为N+1，通常能实现最优的利用率；即当计算密集型的线程偶尔由于页缺失故障或者其他原因而暂停时，这个额外的线程也能确保CPU的时钟周期不会被浪费；
7. 对于包含I/O操作或者其他阻塞操作的任务，由于线程并不会一直运行，因此线程池的规模应该更大；
8. 要正确的设置线程池的大小，必须估算出任务的等待时间与计算时间的比值；这种估算不需要很精确，并且可以通过一些分析或监控工具来获得；
9. 要使处理器达到期望的使用率，线程池的最优大小等于：`N_thread=N_cpu*U_cpu*(1+W/C)`
    * N_cpu：cpu个数，Runtime.getRuntime().availableProcessors()；
    * U_cpu：目标cpu利用比例，(U_cpu取值范围[0,1])；
    * W/C：任务等待时间与计算时间的比例；
10. CPU周期并不是唯一影响线程池大小的资源，还包括内`存、文件句柄、套接字句柄和数据库连接`等。计算这些资源对线程池的约束条件是更容易的：`线程池大小的上限=该资源的可用总量/每个任务的需求量`

# 配置ThreadPoolExecutor
1. ThreadPoolExecutor为一些Executor提供了基本的实现，这些Executor是由Executors中的newCachedThreadPool、newFixedThreadPool和newScheduledThreadExecutor等工厂方法返回的；
2. ThreadPoolExecutor是一个灵活的、稳定的线程池，允许进行各种定制；
3. 如果默认的执行策略不能满足需求，那么可以通过ThreadPoolExecutor的构造函数来实例化一个对象，并根据自己的需求来定制，并且可以`参考Executors的源码`来了解默认配置的执行策略，然后再以执行执行策略为基础进行修改；
```java
public ThreadPoolExecutor(int corePoolSize,int maxmimumPoolSize,long keepAliveTime,TimeUnit unit,BlockingQueue<Runnable> workQueue,ThreadFactory threadFactory,RejectedExecutionHandler handler){
    ...
}
```
## 线程的创建和销毁
1. 线程池的基本大小（`Core Pool Size`）、最大大小（`Maximum Pool Size`）以及存活时间（`Idle Time`）等因素共同负责线程的创建和销毁；
2. 通过调节线程池的基本大小和存活时间，可以帮助线程池`回收空闲线程占有的资源`，从而使得这些资源可以用于执行其他工作。（显然这是一种折衷：回收空闲线程会产生额外的`延迟`，因为当需求增加时，必须创建新的线程来满足需求）；
3. newFiexedThreadPool工厂方法将线程池的基本大小和最大大小设置为参数中指定的值，而且创建的线程池不会超时；
4. newCachedThreadPool工厂方法将线程池的最大大小设置为Integer.Max_VALUE，而将基本大小设置为0，并将超时设置为60S，这种方法创建出来的线程池可以被无限扩展，并且当需求降低时会自动收缩；
5. 其他形式的线程池可以通过显式的ThreadPoolExecutor构造函数来构造；

## 管理队列任务
在有限的线程池中会限制可并发执行的任务数量。
ThreadPoolExecutor允许提供一个BlockingQueue来保存等待执行的任务。基本的任务排队方法有3种：无界队列、有界队列和同步移交（Synchronous Handoff）。队列的旋转与其他配置参数有关，例如线程池的大小等；
1. newFixedThreadPool和newSignleThreadPool在默认情况下将使用一个无界的LinkedBlockingQueue；
2. 一种更稳妥的资源管理策略是使用有界队列，如ArrayBlockingQueue、有界的LinkedBlockingQueue、PriorityBlockingQueue；
3. 当使用像LinkedBlockingQueue或ArrayBlockingQueue这样的FIFO队列时，任务的执行顺序与它们的到达顺序相同；
4. 如果想进一步控制任务的执行顺序，还可以使用PriorityBlockingQueue，这个队列将根据优先级来安排任务，任务的优先级是通过自然顺序或Comparator来定义的；

有界队列避免资源耗尽情况发生，但是它又带来了新的问题，当队列填满后，新的任务改怎么办？有许多饱和策略(Saturation Policy)；
在使用有界队列时，队列的大小和线程池的大小必须一起调节，如果线程池较小而队列较大，那么有助于减少内存使用量，降低CPU的利用率，同时可以减少上下文切换，但付出的代价的是可能会限制吞吐量；
只有当任务相互独立时，为线程池或工作队列设置界限才是合理的。如果任务之间存在依赖性，那么有界的线程池或队列就可能出现`饥饿`死锁问题。此时应该使用无界的线程池，例如newCachedThreadPool;

### SynchronousQueue（工作移交）
1. 对于非常大的或者无界的线程池，可以通过使用SynchronousQueue来避免任务排队。以及直接将任务从生产者移交给工作者线程；
2. SynchronousQueue不是一个真正的队列，而是一种在线程之间进行移交的机制。要将一个元素放入SynchronousQueue中，必须有另一个线程正在等待接收这个元素，如果没有线程正在等待，并且线程池的当前大小小于最大值，那么ThreadPoolExecutor将创建一个新的线程，否则根据饱和策略，这个任务将被拒绝；
3. 使用直接移交将更高效，因为任务会直接移交给执行它的线程，而不是被首先放在队列中，然后由工作者线程从队列中提取该任务。
4. 只有当线程池是无界的或者可以拒绝任务时，SynchronousQueue才有实际价值;
5. 在newCachedThreadPool工厂方法中就使用了SynchronousQueue;

>对于Executor，newCachedThreadPool工厂方法是一个很好的默认选择，它能提供比固定大小的线程池更好的队列性能，
>当需要限制当前任务的数量以满足资源管理需要时，那么可以选择固定大小的线程池，就像在接收网络客户请求的服务器应用程序中，如果不进行限制，那么很容易出现过载问题；

## 饱和策略
1. 当队列被填满后，饱和策略开始发挥作用。ThreadPoolExecutor的饱和策略可以通过调用setRejectedExecutionHandler来修改；
2. JDK提供了胡值班费有人的RejectedExecutionHandler来实现，每种实现都包含有不同的策略：AbortPolicy、CallerRunsPolicy、Discardpolicy和DiscardOldestPolicy；
3. 中止策略(`AbortPolicy`)是默认的饱和策略，该策略将抛出RejectedExecutionException；
4. 当心提交的任务无法保存到队列中等待执行时，抛弃策略(`DiscardPolicy`)会悄悄抛弃该任务；
5. 抛弃最旧的策略(`DiscardOldestPolicy`)会抛弃下一个将被执行的任务，然后尝试重新提交新的任务。如果工作队列是一个PriorityQueue，那么DiscardOldestPolicy将抛弃优先级最高的任务，因此最好不要将DiscardOldestPolicy饱和策略和PriorityQueue放在一起；
6. 调用者运行策略(`CallerRunsPolicy`)实现了一种调节机制，该策略既不会抛弃任务，也不会抛弃异常，而是将某些任务回退到使用者，从而降低新任务的流量；它不会再线程池的某个线程中执行新提交的任务，而是在一个掉了execute线程中执行该任务。
7. 当创建Executor时，可以选择饱和策略或者对执行策略进行修改；
```java
ThreadPoolExecutor executor=new ThreadPoolExecutor(N_THREADS,N_THREADS,0L,TimeUnit.MILLISECONDS,new LinkedBlockingQueue<Runnable>(CAPACITY));
executor.setRejectedExecutorHandler(new ThreadPoolExecutor.CallerRunsPolicy());     
```
8. 当工作队列被填满后，没有预定义的饱和策略来阻塞execute。然而，通过使用Semaphore（信号量）来限制任务的到达率；就可以实现这个功能。
```java
@ThreadSafe
public class BoundedExecutor{
    private final Executor exec;
    provate final Semaphore semaphore;

    public BoundedExecutor(Executor exec,int bound){
        this.exec=exec;
        this.semaphore=new Semaphore(bound);
    }

    public void submitTask(final Runnable command){
        semaphore.acquire();
        try {
            exec.execute(new Runnable(){
              public void run(){
                try {
                    command.run();
                } finnallu {
                    semaphore.release();
                }
              }
            });
        } catch (RejectedExecutionException e) {
            semaphore.release();
        }
    }
}

```
## 线程工厂
```java
public interface ThraedFactory{
    Thread newThread(Runnable r);
}
```
1. 自定义线程工厂
自定义线程工厂，实现在线程转储和错误日志信息中区分来自不同线程池的线程
```java
public class MyThreadFactory implements ThreadFactory{
    private final String poolName;
    public MyThreadFactory(String poolName){
        this.poolName=poolName;
    }
    public Thread newThread(Runnable runnable){
        return new MyAppThread(runnable,poolName);
    }
}
```
在MyThreadApp中定制行为：指定线程名字，设置自定义UncaughtExceptionHandler向Logger中写入信息，维护一些统计信息（包括多少个线程被创建和销毁），以及在线程被创建或者终止时把调试消息写入日志；
```java
public class MyAppThread extends Thread{
    public static final String DEFAULT_NAME="myAppTherad";
    private static volatile boolean debugLifecycle=false;
    private static final AtomicInteger created=new AtomicInteger();
    private static final AtomicInteger alive=new AtomicInteger();
    private static final Logger log =Logger.getAnonymousLogger();

    public MyAppthread(Runnable r){
        this(r,DEFAULT_NAME);
    }

    public MyAppThread(Runnable runnable,String name){
        super(runnable,name+"-"+created.incrementAndGet());
        setUncaughtExceptionHandler({
            new Thread.UncaughtExceptionHandler(){
                public void uncaughtException(Thrad t,Throwable e){
                    log.log(Level.SEVERE,"UNCAUGHT in thread"+t.getName(),e);
                }
            }
        });
    }

    public void run(){
        //复制debug标志以保证唯一性
        boolean debug=debugLifecycle;
        if(debug){
            log.log(Level.FINE,"Created "+getName());
        }
        try{
            alive.incrementAndGet();
            super.run();
        }finally{
            alive.decrementAndGet();
            if(debug){
                log.log(Level.FINE+"Exiting "+getName());
            }
        }
    }
    public static int getThreadsCreated(){return created.get()};
    public static int getThreadAlive(){return alive.get();}
    public boolean getDebug(){return debugLifecycle;}
    public static void setDebug(boolean b){debugLifecycle=b};
}
```

## 在调用构造函数后再定制ThreadPoolExecutor
1. 在调用万ThreadPoolExecutor的构造函数后，仍然可以通过setter方法来修改大多数传递给它的构造函数的参数：线程池的基本大小、最大大小、存活时间、线程工厂、拒绝执行器；
2. 如果Executor是通过Executors中的某个（newSingleThreadPoolExecutor除外）工厂方法创建的，那么可以将结果的类型转换为ThreadPoolExecutor以访问setter方法；
3. 在Executors中包含一个FinalizableDelegatedExecutorService工厂方法，该方法对一个现有的ExecutorService进行包装，使其只报了出ExecutorService的方法，因此不能对它进行配置；newSingleThreadPoolExecutor返回按这种方法封装的ExecutorService，而不是最初的ThreadPoolExecutor；
4. 如果将ExecutorService暴露给不信任的代码，又不希望对其进行修改，就可以通过FinalizableDelegatedExecutorService来包装它；

# 扩展ThreadPoolExecutor
1. ThreadPoolExecutor是可扩展的，它提供了几个可以在子类中改写的方法：`beforeExecute`、`afterExecute`和`terminated`，这些方法可以用于扩展ThreadPoolExecutor的行为；
2. 在执行任务的线程中将调用`beforeExecute`和`afterExecute`等方法，在这些方法中还可以添加日志、计时、监视或统计信息收集的功能；
3. 在线程完成关闭操作时调用`terminated`，也就是在所有任务都已经完成并且所有工作者线程也已经关闭后。terminated可以用来释放Executor在其生命周期里分片的各种资源，此外还可以执行发生通知、记录日志或者手机finalize统计等信息；

# 递归算法的并行化

# 小结