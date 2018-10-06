取消与关闭
---
>任务和线程的启动很容易，但是如何`安全、快速、可靠`的`停止`下来？
>生命周期结束（`End-of-LifeCycle`）的问题会使任务、服务以及程序的设计和实现等过程变得复杂，而这个在程序设计中非常重要的要素却经常被忽略。
>一个在行为良好的软件与勉强运行的软件之家的最主要的区别就是，行为良好的软件能很完善的处理失败、关闭和取消等过程；
>本章将给如各种实现`取消`和`中断`的机制，以及如何编写任务和服务，使他们能对取消请求做出响应；
<!-- TOC -->

- [任务取消](#任务取消)
    - [中断](#中断)
    - [中断策略（interruption Policy）](#中断策略interruption-policy)
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

## 中断策略（interruption Policy）
1. 正如需要为任务制定任务取消策略一样，也应该制定线程的中断策略；
2. 一个中断策略决定线程如何面对中断请求:
    * 当发现中断请求时，它会做什么；
    * 哪些工作单元对于中断来说是原子操作；
    * 在多快的时间内响应中断；
3. 中断策略中最有意义的是对线程级（thread-level）和服务级（service level）取消的规定：
    * 尽可能`快速退出`，
    * 如果需要的话进行`清理`，
    * 可能的话`通知`其拥有的实体，这个线程已经退出；
    * 很可能建立其他中断策略，比如`暂停和重新开始`，但是那些具有非标准中断策略的线程或线程池，需要被约束于那些应用了该策略的任务中；
4. 区分任务和线程对中断的反应是很重要的。
* 一个单一的中断请求可能有一个或一个以上的预期的接收者；
* 在线程池中中断一个工作者线程，意味着取消当前任务，并关闭线程；
5. 任务不会再自己拥有的线程中执行：他们借用服务的线程，比如线程池；
6. 代码如果并不是线程的拥有者，就应该小心的保存中断状态，这样所有者的代码才能最终对其起到作用；
7. 为什么大多数可阻塞的库函数，仅仅抛出InterruptedException作为中断的响应？
他们绝不可能自己运行在一个线程中，所以他们为任务或者库代码实现了大多数合理的取消策略：它们会尽可能快的为异常信息让路，把它们向后传给调用者，这样上层栈的代码就可以进一步行动了；
8. 当检查到中断请求时候，任务并不需要放弃所有事情，它可以选择推迟，直到更合适的时机：这就需要记住她已经被请求过中断，完成当前正在卞的任务，然后排除InterruptedException或指明中断。当更新的过程发生中断时，这项技术能够`保证数据结构不被彻底破坏`；
9. 一个任务不应该假设其执行线程的中断策略，除非任务显式的设计用来运行在服务中，并且这些服务有明确的中断策略；
10. 无论任务把中断解释为取消，还是其他的一些关于中断的操作，它都应该注意保存执行线程的中断状态；如果对中断的处理不仅仅是把InterruptedException传递给调用者，那么它应该在捕获InterruptedException之后恢复中断的状态；
`Thread.currentThread().interrupt();`
11. 正常任务代码不应该假设猜测中断对于它的执行策略意味着什么，取消代码也不应该对任何线程的中断策略进行假设；线程应该只能够被线程的所有者中断；所有者可以把线程的中断策略信息封装到一个`合适的取消机制`中，比如`关闭（shutdown）`方法；
12. `因为每一个线程都有自己的中断策略，所以你不应该中断线程，除非你知道中断对这个线程意味着什么`；
13. 批评者嘲笑Java的中断工具，因为它没有提供优先中断的能力，而且还强迫开发者处理InterruptedException。但是`推迟中断请求的能力`使开发者能够制定`更灵活的中断策略`，从而实现适合于程序的`响应性`和`健壮性`之间的平衡；
## 响应中断
1. 当调用可中断的阻塞函数时，比如Thread.sleep或者blockingQueue.put,有2种处理InterruptedException的实用策略：
    * 传递异常（很可能发生在特定任务的清除时），使你的方法也成为可中断的阻塞方法；
    * 保存中断状态，上层调用栈中的代码能够对其进行处理；
2. 只有实现了线程中断策略的代码才可以接收中断请求，通用目的的任务和库的代码绝不应该接收中断请求；
## 示例：计时运行
不要尝试在外部线程中安排中断
## 通过Future来实现取消
在一个专门的线程中中断任务
```java
public static void timeRun(final Runnable r,long timeout,TimeUnit unit) throws InterruptedException{
    class RethrowabeTask implements Runnable{
        private volatile Throwable t;
        public void run(){
            try{
                r.run;
            }catch(Throwable t){
                this.t=t;
            }
        }
        void rethrow(){
            if(t!=null){
                throw launderThrowable(t);
            }
        }
    }
    RethrowabeTask task=new RethrowabeTask();
    final Thread taskThread=new Thread(task);
    taskThread.start();
    cancelExec.schedule(new Runnable(){
       public void run(){
           taskThread.interrupt();
       } 
    },timeout,unit);
    taskThread.join(unit.toMillis(timeout));
    task.rethrow();
}
```
通过Future来取消任务
```java
public static void timeRun(Runnable r,long timeout,TimeUnit unit)throws interruptedException{
    Future<?> task=taskExec.submit(r);
    try{
        task.get(timeout,unit);
    }catch(TimeoutException e){
        //下面任务会被取消
    }catch(ExecutionException e){
        //task中抛出的异常：重抛出
    }finally{
        //如果任务已经结束，是无害的
        task.cancel(true);
    }
}
```
## 处理不可中断的阻塞
1. 很多可阻塞的库方法通过提前返回和抛出InterruptedException来实现对中断的响应，这使得构建可以响应取消的任务更加容易了；
2. 但并不是所有阻塞方法或阻塞机制都响应中断；
3. 如果一个线程是由于进行同步Socket I/O或者等待获得内部锁而阻塞的，那么中断除了能够设置线程的中断中断状态以外，什么都不能改变。
4. 对于那些被不可中断的活动所阻塞的线程，我们可以使用与中断类似的手段，来确保可以停止这些线程。但是这需要我们更清楚的知道线程为什么会被阻塞？
* `java.io中的同步Socket I/O`。在服务器应用程序中，阻塞I/O最常见的形式是读取和写入Socket。不幸的是，InputStream和OutStream的read和write方法都不响应中断，但是通过关闭底层的Socket，可以让read和write所阻塞的线程抛出一个SocketException；
* `java.nio中的同步I/O`。中断一个等待InterruptibeChannel的线程，会导致抛出ClosedByInterruptedException,并关闭链路；关闭一个InterruptibleChannel导致多个阻塞在链路操作上的线程抛出AsynchronousCloseExcepticount>0on。大多数标准channels都实现InterruptibleChannel;
* `Selector的异步I/O`。如果一个线程阻塞与Selector.select方法，close方法会导致它通过抛出CloseSelectionException提前返回；
* 获得锁。如果一个线程在等待内部锁，那么如果不能确保它最终获得锁，并且作出足够多的努力，让你能够以其他方式获得它的注意，你是不能停止它的。然而，`显式Lock类提供了lockInterruptibly方法`，允许你等待一个锁，并仍然能够响应中断；
## 采用newTaskFor来封装非标准的取消
```java
public class ReaderThread extends Thread{
    private final Socket socket;
    private final InputStream in;
    public ReaderThread(Socket socket) throws IOException{
        this.socket=socket;
        this.in=socket.getInputStream();
    }
    public void interrupt(){
        try{
            socket.close();
        }catch(IOException ignored){
            
        }finally{
            //支持标准的中断
            super.interrupt();
        }
    }

    public void run(){
        try{
            bytep[ buf=new bytep[BUFSZ];
            while(true){
                int count=in.read(buf);
                if(count <0){
                    break;
                }
                else if(count>0){
                    processBuffer(buf,count);
                }
            }
            catch(IOException e){
                //允许线程退出
            }
        }
    }
}
```
# 停止基于线程的服务
1. 没有退出线程管用的优先方法，它们需要自行结束；
2. 像其他对象一样，线程可以被自由的共享，但是认为线程有一个对应的共享者是有道理的，这个拥有者就是创建线程的类。所以线程池拥有的工作者线程，如果需要中断这些线程，那么应该由线程池来执行；
3. 正如其他封装的对象一样，线程的所有权是不可传递的：
    * 应用程序可能拥有服务，服务可能拥有工作者线程；
    * 但是应用程序并不拥有工作者线程，因此应用程序不应该试图直接停止工作者线程；
    * 相反，服务应该提供生命周期方法（lifecycle methods）来关闭它自己，并关闭它所拥有的线程；
    *  ExecutorService提供了shutdown和shutdownNow方法，其他线程持有的服务也应该都提供类似的关闭服务；

4. 用newTaskFor封装任务中非标准取消
```java
public interface CancellableTask<T> extends Callable<T>{
    void Taskcancell();
    RunnableFuture<T> newTask();
}
@ThreadSafe
public class CancellingExecutor extends ThreadPoolExecutor{
    protected<T> RunnableFuture<T> newTaskFor(Callable<T> callable){
        if(callable instanceof CancellableTask){
            return ((CancellableTask<T>)callable).newTask();
        }else{
            return super.newTaskFor(callable);   
        }
    }
}

public abstract class SocketUsingTask<T> implements CancellableTask<T>{
    @GuardBy("this")
    private Socket socket;

    protected asynchronized void setSocket(Socket s){
        socket=s;
    }

    public sychronized void cancel(){
        try{
            if(socket!=null){
                socket.close();
            }
        }catch(IOException ignored){}
    }

    public RunnableFuture<T> newTask(){
        return new Futuretask<T>(this){
            public boolean cancell(boolean myInterruptIfRunning){
                try{
                    SocketUsingTask.this.cancel();
                }finally{
                    return super.cancel(myInterruptIfRunning);
                }
            }
        };
    }

}
```
## 示例：日志服务
向LogWriter添加可靠的取消
```java
public class LogService{
    private final BlockingQueue<Stirng> queue;
    private final LoggerThread loggerThread;
    private final PrintWriter writer;
    @GuardBy("this")
    private boolean isShutdown;
    @GuardBy("this")
    private int reservations;
    
    public void start(){
        loggerThread.start();
    }
    
    public stop(){
        synchronized(this){
            iShutdown=true;
        }
        loggerThread.interrupt();
    }
    
    public void log(String msg)throw InterruptedException{
        //创建新日志活动的各个子任务必须是原子的
        synchronized(this){
            if(isShutdown){
                throw new IllegalStateException("...");
            }
            ++reservations;
        }
        queue.put(msg);
    }

    public class LoggerThread extends Thread{
        public void run(){
            try{
                while(true){
                    try{
                        //原子化检查关闭
                        synchronized(LogService.this){
                            if(isShutdown &&reservations==0){
                                break;
                            }
                        }
                        String msg=queue.take();
                        synchronized(LogService.this){
                            --reservations;
                        }
                        writer.printlin(msg);
                    }catch(InterruptedException e){
                        //重试
                    }
                }
            }finally{
                writer.close();
            }
        }
    }
}
```
## 关闭ExecutorService
ExecutorService提供了关闭的两种方法：
1. 使用shutdown优雅的关闭，
2. 使用shutdownNow强行的关闭：首先尝试关闭当前正在执行的任务，然后返回待完成任务的清单；

两种不同的终结选择在`安全性`和`响应性`之间进行了权衡：强行终结的速度更快，但是风险大，因为任务可能在执行到一半的时候被终结，而正常终结虽然速断慢，却安全，因为知道队列中的所有任务完成前，ExecutorService都不会关闭。

## `毒丸`对象
1. 另一种保证生产者和消费者服务关闭的方式是使用毒丸对象(`poision pill`):一个可识别的对象，置于队列中，意味着`当你得到它时，停止一切工作`。
2. 在FIFO队列中毒丸对象保证了消费者完成队列中关闭之前的所有工作，因为所有早于致命毒丸提交的工作都会在处理它之前就完成了；
3. 生产者不应该在提交了毒丸后，再提交任何工作；
4. 毒丸只有在生产者和消费者数量已知的情况下使用。
5. 毒丸只有在无限队列中工作时，才是可靠的；

## 示例：只执行一次的服务

## shutdownNow的局限性
shutdownNow时没有常规方法获取已经提交但没有开始的任务的清单；
在关闭过程中判定哪些任务还在进行的技术；
```java
public class TrackingExecutor extends AbstractExecutorService{
    private final ExecutorService exec;
    private final Set<Runnable> taskCancelledAtShutdown=Collections.synchronizedset(new HashSet<Runnable>());
    ...
    public List<Runnable> getCancelledTasks(){
        if(!exec.isTerminated()){
            throw new IllegalStateException(...);
        }
        return new ArrayList<Runable>(taskCancelledAtShutdown); 
    }
    public void execute(final Runable runnable){
        exec.execute(new Runnable(){
            public void run(){
                try{
                    runnable.run();
                }finally{
                    if(isShutdown()&& thread.currentThread().isInterrupted()){
                        taskCancelledAtShutdown.add(runnable);
                    }
                }
            }
        })
    }
}
//奖ExecutorService中的其他方法委托到exec；
```
# 处理非正常的线程终止

# JVM关闭
## 关闭钩子
## 守护线程
## 终结器

# 小结
