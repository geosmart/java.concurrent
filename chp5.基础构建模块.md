基础构建模块
---
>Java平台类库包含了丰富的`并发基础构建模块`，例如线程安全的`容器类`以及各种用于协调多个`相互协作的线程控制流`的`同步工具类`（Synchronizer）。
本章介绍一些最有用的并发构建模块；
[TOC]
# 同步容器类
1. 同步容器类包括Vector和Hashtable,好包括在JDK中提供的Collection.synchronizedXxx等工厂方法创建的；
2. 这些类实现线程安全的方法是：将他们的状态封装起来，并且对每个共有方法都进行同步，使得每次都只有一个线程能访问容器的状态；
## 同步容器类的问题
同步容器类都是`线程安全`的，但在某些情况下需要额外的`客户端加锁`来保证`复合操作`：
* 迭代，iterator.next()
* 跳转，get(i)
* 条件运算,putIfAbsent()

1. 在同步容器类中，这些复合操作没有客户端加锁的情况下仍然是线程安全的；但当其他线程并发的修改容器时，他们可能会表现出意料之外的行为；
2. 在客户端加锁可以解决ArrayIndexOutOfBoundsException问题，但要牺牲一些性能（同步阻塞降低了并发性）；
## 迭代器与ConcurrentModificationException
1. 在设计同步容器类的迭代器时并没有考虑并发修改的问题，并且它们表现出来的行为是failfast(ConcurrentModificationException)；
2. 这是一种设计上的平衡，从而降低并发修改操作的监测代码对程序性能带来的影响；
3. 在迭代过程要想避免出现ConcurrentModificationException，必须`对持有容器加锁`；
4. 长时间对容器加锁会降低程序的可伸缩性，降低吞吐量和CPU利用率；
5. 如果不希望对容器加锁，可以`clone容器`，在副本上进行迭代，由于副本封闭在线程内，其他线程不会在迭代期间对其进行修改，避免了抛出ConcurrentModificationException；
6. clone容器存在明显的`性能开销`，具体使用取决于容器大小，每个元素迭代的工作，迭代操作相对于容器其他操作的调用频率，以及在响应时间和吞吐量等方面的需求；
## 隐蔽迭代器
toString,hashCode,equals等方法会`间接的执行迭代操作`，当容器作为另一个容器的元素或键值时，就可能会抛出ConcurrentModificationException；

>正如`封装对象`的状态有助维持`不变性条件`一样，`封装对象的同步机制`同样有助于确保实施`同步策略`；

# 并发容器
1. 同步容器将所有对容器状态的访问都`串行化`，以实现他们的`线程安全性`；这种方法的代价是严重`降低并发性`，当多个线程竞争容器的锁时，`吞吐量`将严重降低；
2. 并发容器是针对多个线程并发访问设计的；
3. Java5中新增了同步容器ConcurrentHashMap，CopyOnWriteArrayList以替代同步容器，降低风险并提高伸缩性；
4. Java5新增了两种容器类型，Queue和BlockingQueue；
5. Java6引入ConcurrentSkipListMap和ConcurrentSkipListSet分别作为SortedMap和SortedSet的并发替代品；
6. Queue用来临时保存一组等待处理的元素，它提供了几种实现，包括ConcurrentLinkedQueue（传统的FIFO队列）；以及PriorityQueue（非并发的优先队列）；
    * Queue上的操作不会阻塞，如果队列为空，那么获取元素的操作将返回空值；
    * 通过LinkedList来实现Queue，同时需要一个Queue类来去掉List的随机访问，从而实现更高效的并发；
7. BlockingQueue扩展了Queue，增加了可阻塞的插入和获取等操作；
    * 如果队列为空，那么获取元素的操作将一直阻塞，直到队列中出现一个可用的元素；
    * 如果队列已满，那么插入元素的操作将一直阻塞，直到队列中出现可用的空间
## ConcurrentHashMap
1. HashMap：get或contains会调用equals方法，如果`散列函数`很糟糕会把一个散列表变成`线性链表`，当遍历很长的链表（在元素的equals调用上耗时很长）时，其他线程在这段时间内都不能访问该容器；
2. ConcurrentHashMap：采用完全不同的加锁策略。不是将每个方法都在同一个锁上进行同步并使得每次只能有一个线程访问容器；而是使用一种`粒度更细`的加锁机制来实现更大程度的共享，这种机制称为分段锁（Lock Striping）。
3. `分段锁（Lock Striping）`机制机制中，`任意数量的读取线程`可以并发的读取Map,执行`读取`操作的线程和执行`写入`操作的线程可以`并发的访问Map`,并且一定数量的写入线程可以`并发的修改Map`；
4. ConcurrentHashMap带来的结果是，在并发环境下将实现更高的`吞吐量`，而在单线程环境中只损失非常小的性能；
5. ConcurrentHashMap与其他并发容器一起增强了同步容器类：它提供的迭代器不会抛出ConcurrentModificationException，因此不需要在迭代过程中对容器加锁，`弱一致性的迭代器`可以容忍并发的修改；
6. ConcurrentHashMap的权衡因素：对一些需要在整个Map上进行计算的方法，如Size和isEmpty，这些方法的语义被略微减弱了以反映容器的并发特性。
7. ConcurrentHashMap的size实际上是一个估计值，因此允许size返回一个`近似值`而不是精确值，虽然这看上去令人不安，但事实上size和Empty方法在`并发环境`下的用处很小，因为他们的`返回值总是在不断变化`；因此这些操作需求被弱化，以换取对其他更重要操作的性能优化，包括get、put、containsKey、remove等；
8. ConcurrentHashMap与HashMap、SynchronizedMap相比有更多的优势以及更少的劣势，因此在大多数情况下，用ConcurrentHashMap来替代同步Map能进一步提高代码的`伸缩性`。只有当应用程序需要`加锁Map以进行独占访问`时，才应该放弃使用ConcurrentHashMap;
## 额外的原子Map操作
如果需要在现有的同步Map中添加复合原子操作，那就意味着应该考虑ConcurrentMap了；
```java
public interface ConcurrentMap<K,V> extends Map<K,V>{
    V putIfAbsent(K key,V value);
    boolean remove(K key,V value);
    boolean replace(K key,V oldValue,V newValue);
    V replace(K key,V newValue);
}
```
## CopyOnWriteArrayList
CopyOnWriteArrayList用于替代同步List，在某些情况下它提供了更好的`并发`性能，并且在`迭代`期间不需要对容器进行`加锁`或`复制`（类似的，`CopyOnWriteArraySet`的作用是替代同步的Set）；
1. 写入时复制（ copy-on-write）容器的线程安全性在于，只要正确的发布一个`事实不可变对象，那么访问该对象就不需要进一步的同步；
2. 在每次修改时，都会创建并重新发布一个新的`容器副本`，从而实现可变性；
3. `copy-on-write`容器的迭代器保留一个`指向底层基础数组的引用`，这个数组当前位于迭代器的起始位置，由于这个数组不会被修改，所以对其进行同步只需要`确保数组内容的可见性`（volatile）；
4. 因此多个线程可以`同时对容器进行迭代`，而不会彼此干扰或者与修改容器的线程互相干扰；
5. 显然，每当修改容器时都会复制底层数组，这需要一定的`开销`，特别是当容器规模较大时；
6. 仅当`迭代器操作`远远多于`修改操作`时，才应该使用`copy-on-write`容器；这个准则很好的描述了`事件通知系统`：在分发通知时需要迭代已注册监听器链表，并调用每一个监听器，在大多数情况下，`注册和注销监听器的操作远少于接收事件通知的操作`；

# 阻塞队列和生产者-消费者模式(Producer-Consumer)
1. 阻塞队列提供了`可阻塞`的`put`和`take`方法，以及支持`定时`的`offer`和`poll`方法；
    * 如果队列满了，`put`方法会阻塞直到队列有空间可用；
    * 如果队列为空，`take`方法会阻塞直到有元素可用；
2. 队列可以是有界的也可以是无界的，无界队列永远都不会满，因此无界队列的put方法也永远不会阻塞；
3. 阻塞队列支持`生产者-消费者设计模式`，生产者-消费者模式能`简化开发过程`，因为它能消除`生产者`类和`消费者`类之间的代码依赖性；
4. 生产者-消费者模式模式还将生产数据和消费数据的过程`解耦`开来以`简化工作负载的管理`，因为这两个过程在`处理数据的速率`上有所不同；
5. BlockingQueue简化了生产者-消费者的设计过程，它支持任意数量的生产者和消费者。一种最常见的生产者-消费者设计模式就是`线程池`与`工作队列`的组合,在`Executor任务框架`中就提现了这种模式；
6. 生产者和消费者的角色是相对的，某种环境中的消费者在另一种不同的环境中可能会成为生产者；
7. 如果生产者生成工作的速率比消费者处理工作的速率快，那么工作项就会在队列中累计起来，最终耗尽内存；如果使用有界队列，`put方法`的阻塞特性极大简化了生产者的编码，生产者阻塞并且不能继续生成工作，而消费者就有时间来赶上工作处理进度；
8. 阻塞队列的`offer方法`：如果数据项不能添加到队列中，将返回一个失败状态。这样你就能够创建更多灵活的策略来`处理负荷过载`的情况，如减轻负载，将多余的工作项序列化并写入磁盘，减少生产者线程的数量，或者通过某种方式来抑制生产者线程；
9. JDK类库中包含了多种BlockingQueue的实现：
    * `LinkedBlockingQueue`和`ArrayListBlockingQueue`是`FIFO队列`，二者与LinkedList和ArrayList类似，但比同步List拥有更好的并发性能；
    * `PriorityBlockingQueue优先队列`，是一个按优先顺序排列的队列，可以`按元素的顺序`（实现Comparable方法或使用Comparator来比较）而不是FIFO来处理元素；
    * `SynchronousQueue同步队列`，它不是一个真正的队列，因为它不会为队列中元素维护存储空间，它维护一组线程，这些线程在等待着把元素加入或移出队列；这种队列实现的方式看似很奇怪，但由于可以直接交付工作，从而`降低了将数据从生产者移动到消费者的延迟`，并可以`直接将任务信息反馈给生产者`。因为SychronousQueue没有存储功能，因此put和take会一直阻塞，直到有另一个线程已经准备好参与到交付过程中；仅当`拥有足够多的消费者`，并且总是有一个消费者准备获取交付的工作时，才适合使用`同步队列`；

## 示例：桌面搜索
1. 生产者-消费者模式提供了一种适合线程的方法将桌面搜索问题分解为简单的组件。
2. 将`文件遍历`与`建立索引`等功能分解为独立的操作，比将所有功能都放到一个操作中实现有着更高的`代码可读性`和`可重用性`：每个操作只完成一个任务，并且阻塞队列将负责所有的控制流，因此每个功能的代码都更加`简单和清晰`；
3. 生产者-消费者模式同样能带来许多性能优势。生产者和消费者可以`并发的执行`。如果一个是`I/O密集型`，另一个是`CPU密集型`，那么并发执行的吞吐率要高于串行执行的`吞吐率`。如果生产者和消费者的并行度不同，那么将他们`紧耦合`在一起会把整体的并行度降低为二者中更小的并行度；

## 串行线程封闭
1. java.util.concurrent的阻塞队列的内部同步机制：对于可变对象，生产者-消费者这种设计与阻塞队列一起，促进了`串行线程封闭`，从而将对象所有权从生产者交付给消费者；
2. `线程封闭对象`只能由单个线程拥有，但可以通过安全的发布该对象来`转移所有权`。在转移所有权后，也只有另一个线程能获得这个对象的访问权限，并且发布对象的线程不会再访问它。这种`安全的发布`确保了对象状态对于新的所有者来说是`可见`的，并且由于最初的发布者不会再发布它，因此对象将被`封闭在新的线程`中；新的所有者线程对对象拥有独占的访问权；
3. `对象池`利用了串行线程封闭，将对象borrow给一个请求线程。只要对象池包含足够的内部同步来安全的发布池中的对象，并且只要客户代码本身不会发布池中的对象，或者在将对象返回给对象池后就不再使用它，那么就可以安全的在线程间传递所有权；
4. 我们也可以使用其他`发布机制`来`传递可变对象的所有权`，但必须确保`只有一个线程能接受被转移的对象`。阻塞队列简化了这项工作。除此之外，还可以通过`ConcurrentHashMap`的原子方法`remove`或者AtomicReference的原子方法`compareAndSet`来完成这项工作；

## 双端队列(Deque)与工作密取(Work Stealing)
1. Java6中新增了两种容器类型：`Deque`(发音deck,double ended queue的缩写)和`BlockingDeque`，它们分别对Queue和BlockingQueue进行了扩展
2. Deque是一个`双端队列`，实现了在队列头和队列尾的高效插入和删除，具体包括ArrayDeque和LinkedBlockingDeque；
3. 正如阻塞队列适用于生产者-消费者模式，双端队列同样适用于另外一种相关模式，即`工作密取（Work Stealing）`；
4. 在生产者-消费者模式中，所有消费者`共享`一个工作队列，而在工作密取的设计中，每个消费者都有各自的`双端队列`；
5. 如果一个消费者完成了自己双端队列中的全部工作，它可以从其他消费者的双端队列末尾`秘密的获取`工作；
6. 密取工作模式比传统的生产者-消费者模式具有更高的`可伸缩性`，这是因为工作者线程不会在单个`共享的任务队列`上发生`竞争`；
7. 大多数时候，消费者线程只访问自己的双端队列，从而极大的减少了竞争；当工作者线程需要访问另一个队列时，它会`从队列的尾部`而不是从头部获取工作，因此进一步降低了队列上的竞争程度；
8. 工作密取非常适用于即是消费者又是生产者问题-`当执行某个工作时可能会导致出现更多的工作`；
9. 如`网页爬虫`，爬取1个页面时会发现有更多的页面需要处理；如`图搜索方法`；如在`垃圾回收阶段对堆进行标记`；都可以通过工作密取机制来实现高效并行；
10. 当一个工作线程找到`新的任务单元`时，它会`将其放到自己队列的末尾`（或者在工作共享设计模式中，放入其他工作者线程的队列中）。当双端队列为空时，它会在另一个线程的队列队尾查找新的任务，从而`确保每个线程都保持忙碌的状态`；

# 阻塞方法与中断方法
阻塞方法必须处理对中断的响应
## 阻塞方法
1. 线程可能会阻塞或暂停执行，原因有多种：
    * 等待I/O操作结束；
    * 等待获得一个锁；
    * 等待从Thread.sleep方法中醒来；
    * 等待另一个线程的计算结果；
2. 当线程阻塞时，它通常被挂起，并处于某种阻塞状态（`Blocked、WATING、TIMED_WAITING`）；
3. 阻塞操作与执行时间很长的普通操作的区别在于：被阻塞的操作必须等到某个`不受它控制的事件`发生后才能继续执行，例如等待I/O操作完成，等待某个锁变成可用，或者等待外部计算结束，当某个外部事件发生后，线程被置回`RUNNABLE`状态，并可以再次被调度执行；
4. BlockingQueue的put和take等方法会抛出`受检查异常`(Checked Exception)InterruptedException；
5. 当某方法抛出`InterruptedException`时，表示该方法是一个`阻塞方法`，如果这个方法被中断，那么它将努力提前结束阻塞状态；
## 中断方法
1. `Thread`提供了interrupt方法，用于`中断线程`或者`查询线程是否已经中断`，每个线程都有一个boolean类型的属性，表示线程的中断状态(isInterrupted)，当线程中断时设置这个状态；
2. 中断是一种`协作机制`。一个线程`不能强制其他线程停止正在执行的操作`而去执行其他的操作；
3. 当线程A中断B时，A仅仅是要求B在执行到某个`可以暂停的地方`停止正在执行的操作（前提是B愿意停止下来）；
4. 最常使用中断的情况就是`取消某个操作`，方法对中断请求的响应度越高，就越容易及时取消那些执行时间很长的操作；
5. 当在代码中调用了一个将抛出`InterruptedException异常`的方法时，你自己的方法也就变成了一个`阻塞方法`，并且必须要`处理对中断的响应`。对于库代码来说，有两种基本选择：
    * `传递InterruptedException`，避开这个异常通常是最明智的策略-只需把InterruptedException传递给方法的调用者；传递异常的方法是根本不捕获该异常，或者捕获该异常，然后在执行某种简单的清理工作后再次抛出这个异常；
    * `恢复中断`。有时候`不能抛出InterruptedException`,例如当代码是Runnable的一部分时，在这些情况下，必须捕获InterruptedException异常，并且通过调研当前线程上的interrupted方法恢复中断状态，这样在调用栈的更高层的代码将看到引发了一个中断；
6. 只有在一种特殊的情况下才能`屏蔽中断`，即对Thread的扩展，并且能控制调用栈上的所有更高层的代码；
```java
public class TaskRunnable implements Runnable{
    BlockingQueue<Task> queue;
    ...
    public void run(){
        try {
            processTask(queue.take());
        } catch (InterruptedException e) {
            //恢复被中断的状态
            Thread.currentThread().interrupt();
        }
    }
}
```
# 同步工具类
1. 在容器类中，`阻塞队列`是一种独特的类，他们不仅能作为`保存对象`的容器，还能`协调生产者和消费者等线程之间的控制流`，因为take和put等方法将阻塞，直到队列达到期望的状态（队列即非空，也非满）；
2. 同步工具类可以是任何一个对象，只要它根据自身的状态来协调线程的控制流；
3. 阻塞队列可以作为同步工具类，其他类型的同步工具类还包括信号量（Semaphore）、栅栏（Barrier）以及闭锁（Latch）；
4. 所有的同步工具类都包含一些特定的结构化属性：它们封装了一些状态，这些状态将决定执行同步工具类的线程是继续执行还是等待，此外还提供了一些方法对状态进行操作，以及另一些方法用于高效地等待同步工具进入到预期状态；

## 闭锁（Latch）
1. 闭锁是一种同步工具类，可以延迟线程的进度直到其到达终止状态；
2. 闭锁的作用相当于一扇门：
    * 在闭锁到达结束状态之前，这扇门一直是关闭的，并且没有任何线程能通过；
    * 当到达结束状态时，这扇门会打开并允许所有的线程通过；
    * 当闭锁到达结束状态后，将不会再改变状态，因此这扇门将永远保持打开状态；
3. 闭锁可以用来确保某些活动直到其他活动都完成后才继续执行，例如：
    * 确保某个计算在其所需要的所有资源都被初始化之后才继续执行。二元闭锁（包括2个状态）可以用来表示`资源R已经被初始化`，而所有需要R操作都必须在这个闭锁上等待；
    * 确保某个操作在其`依赖的所有服务`都已经启动之后才启动。每个服务都有一个相关的二元闭锁；
    * 等待直到某个操作的所有参与者（如多人游戏中的所有玩家）都就绪后再继续执行，在这种情况下，当所有玩家都准备就绪时，闭锁将到达结束状态；
4. `CountDownLatch`是一种灵活的闭锁实现，可以在上述各种情况中使用，它可以使一个或多个线程等待一组事件发生。
    * 闭锁状态包括一个计数器，该计数器被初始化成一个正数，表示需要等待的事件数量；
    * countDown方法递减计数器，表示有一个事件发生了；
    * await方法等待所有计数器达到0，这表示所有需要等待的事件都已经发生了；
    * 如果计数器的值非0，那么await会一直阻塞直到计数器为0，或者等待中的线程中断，或者等待超时； 
5. 示例：统计线程并发执行时的实际消耗时间
```java
public class TestHarness{
    public long timeTasks(int nThreads,final Runnable task) throw InterruptedException{
        //起始门
        final CountDownLatch startGate=new CountDownLatch(1);
        //结束门
        final CountDownLatch endGate=new CountDownLatch(nThreads);

        for(int i=0;i<nThreads;i++){
            Thread t=new Thread(){
                public void run(){
                    try {
                        startGate.await();
                        try {
                            task.run();
                        } catch (Exception e) {
                            endGate.countDown();
                        }
                    } catch (InterruptedException ignored) {
                        
                    }
                }
            }
        }
        long start=System.nanoTime();
        startGate.countDown();
        long end=System.nanoTime();
        endGate.await();
        return end-start;
    }
}
```
## FutureTask
1. FutureTask也可以用做闭锁。FutureTask实现了Future语义，表示一种抽象的`可生成结果的计算`；
2. FutureTask表示的计算是通过`Callable`实现的，相当于一种可生成结果的Runnable，并且可以处于以下3种状态：等待运行（Waiting to run），正在运行（Running）和运行完成（Completed）；
3. 执行完成表示计算的所有可能结束的方式，包括正常结束，由于取消结束和由于异常结束等。当Future进入完成状态后，它会永远停止在这个状态上。
4. FutureTask的`get`的行为取决于任务的状态。如果任务已经完成，那么get会立即返回结果；否则get将`阻塞`直到任务进入完成状态，然后返回结果或者抛出异常；
5. FutureTask将计算结果从执行计算的线程传递到获取结果的线程，而FutureTask的规范确保了这种传递能实现结果的安全发布；
6. FutureTask在Executor中表示异步任务，此外还可以用来表示一些`时间从较长的计算`，这些计算可以在使用结果之前启动；
7. 示例：Preloader使用了FutureTask来执行一个高开销的计算，并且计算结果将在稍后使用。通过提前启动计算，可以减少结果的等待时间；
```java
public class Preloader{
    private final FutureTask<ProductInfo> future=new FutureTask<ProductInfo>(
        new Callable<ProductInfo>(){
            public ProductInfo call() throws DataLoadException(){
                return loadProductInfo();
            }
        }
    );
    
    private final Thread thread=new Thread(future);
    
    public void start(){
        thread.start();
    }

    //如果数据已经加载，那么将返回这些数据，否则将阻塞等待加载完成后返回；
    public ProductInfo get() throws DataLoadException,InterruptedException{
        try {
            future.get();
        } catch (ExecutionException e) {
            Throwable cause=e.getCause();
            if(cause instanceof DataLoadException){
                throw (DataLoadException)cause;
            }else{
                throw launderThrowable(cause);
            }
        }
    }

    public static RuntimeException launderThrowable(Throwable t){
        if (t instance of RuntimeException){
            return (RuntimeException)t;
        }else if(t instanceof Error){
            throw (Error)t
        }else{
            throw new IllegalStateException("Not unchecked",t);
        }
    }
}
``` 
## 信号量(Semaphore)
1. 计数信号量(Counting Semaphore)用来控制`同时访问`某个特定资源的`操作数量`,或者`同时执行`某个指定操作的数量；
2. 计数信号量还可以用来实现某种`资源池`，或者对容器施加`边界`；
3. Semaphore中管理着一组虚拟的许可(permit)，许可的初始数量可以通过构造函数指定；
4. 在执行操作时首先获得permit，并在使用之后释放permit；如果没有permit,那么acquire将阻塞直到有permit(或者被中断或者操作超时);
5. release方法将释放一个permit给信号量；
6. 计算信号量的一种简化形式是`二值信号量`，即初始值为1的Semaphore。二值信号量可以用做Mutex,并具备不可重入的加锁语义：谁拥有这个唯一的许可，谁就拥有了互斥锁；
7. Semaphore可以用于实现`资源池`，例如`数据库连接池`；
    * 构造一个固定长度的资源池，当池为空时，请求资源将会失败，但真正希望看到的行为是阻塞而非失败，并且当池非空时解除阻塞；
    * 如果将Semaphore的计数值初始化为池的大小，并且从池中获取一个资源之前首先调用acquire方法获取一个permit，在将资源返回给池之后调用release释放许可，那么acquire将一直阻塞直到资源池不为空；（更简单的方法是使用BlockingQueue）
8. SemaPhore可将任何一种容器变成有界阻塞容器，如BoundedHashSet为容器设置边界
```java
public class BoundedHashSet<T>{
    private final Set<T> set;
    private final Semaphore sem;

    public BoundedHashSet(int boud){
        this.set=Collections.synchronizedSet(new HashSet<T>());
        sem=new Semaphore(bound);
    }

    public boolean add(T o)throws InterruptedException{
        sem.acquire();
        boolean warAdded=false;
        try {
            wasAdded=set.add(o);
            return wasAdded;
        } finally {
            //未添加成功，立即释放许可；
            if(!wasAdded){
                sem.release();
            }
        }
    }

    public boolean remove(Object o){
        boolean wasRemoved=set.remove(o);
        if(wasRemoved){
            sem.release();
        }
        return wasRemoved;
    }

}
```
底层的Set不知道关于边界的任何信息，这是由BoundedSet来处理的；

## 栅栏(Barrier)
1. 通过闭锁来启动一组相关的操作，或者等待一组相关的操作结束；闭锁是一次性对象，一旦进入终止状态，就不能被重置；
2. 栅栏（Barrier）类似于闭锁，它能阻塞一组线程直到某个事件发生；
3. Barrier与闭锁的关键区别在于，`所有线程`必须`同时`到达栅栏位置，才能继续执行；
4. `闭锁`用于等待其他`事件`，而`Barrier`用于等待其他`线程`；
5. Barrier用于实现一些协议，如几个家庭决定在某个地方集合："所有人6点在麦丹劳碰头，到了以后要等待其他人，之后再讨论下一步要做的事情"；
6. `CyclicBarrier`可以使`一定数量`的参与方`反复`的在栅栏位置汇集，它在`并行迭代算法`中非常有用；
    * 这种算法通常将一个问题拆分成一系列相互独立的子问题；
    * 当线程到达Barrier位置时将调用await方法，这个方法将`阻塞`直到所有线程到达Barrier位置；
    * 如果线程到达了Barrier位置，那么Barrier将打开，此时所有的线程都被释放，而Barrrier将被重置以便下次使用；
    * 如果对await的调用超时，或者await阻塞的线程被中断，那么Barrier就被认为是打破了，所有阻塞的await调用都将中止并抛出BrokenBarrierException；
    * 如果成功的通过Barrier，那么await将为每个线程返回一个唯一的`到达索引号`，我们可以通过这些索引来`选举`产生一个领导线程，并在下一次迭代中由该领导线程执行一些特殊的工作；
    * CyclicBarrier还可以使你将一个栅栏操作传递给构造函数，这是一个Runnable，当成功通过栅栏时会（在一个子任务线程）执行它，但在阻塞线程被释放之前是不能执行的；
7. 在`模拟程序`中通常需要使用栅栏，例如某个步骤中的计算可以并行执行，但必须等到该步骤中的所有计算都执行完毕才能计算下一个步骤。
8. 例如，在n-body粒子模拟系统中，每个步骤都根据其他粒子的位置和属性来计算各个粒子的新位置，通过在每2次更新之间等待栅栏，能够确保在第K步中的所有更新操作都已经计算完毕，才进入第k+1步；
9. 示例：通过Barrier来计算细胞的自动化模拟；
```java
//通过Barrier来计算细胞的自动化模拟
public class CellularAutomata{
    private final Board mainBoard;
    private final CyclicBarrier barrier;
    private final Worker[] workers;

    public CellularAutomata(Board board){
        this.mainBoard=board;
        //在这种不涉及IO操作或共享数据访问的计算问题中，当线程数为N_cpu或N_cpu+1时将获得最优的吞吐量；更多的先线程不会带来任何帮助，甚至会因为多个线程将会在CPU和内存等资源上发生竞争而降低性能。
        int count=Runtime.getRuntime().avaliableProcessors();
        this.barrier=new CyclicBarrier(count,new Runnable(){
            public void run(){
                mainBoard.commitNewValues();
            }
        });
        //将任务分解成一定数量的子问题，每个子问题分配一个线程
        this.workers=new Worker(count);
        for(int i=0;i<count;i++){
            //工作线程为各子问题中的所有细胞计算新值
            worker[i]=new Worker(mainBoard.getSubBoard(count,i));
        }
    }
    private class Worker implements Runnable{
        private final Board board;
        public Worker(Board board){
            this.board=board;
        }
        public void run(){
            while(!board.hasCoverged){
                for(int x=0;x<board.getMaxX();x++){
                    for(int y=0;y<board.getMaxY();y++){
                        board.setNewValue(x,y,compute(x,y));
                    }
                }
                try {
                    barrier.await();
                } catch (InterruptionException e) {
                    return;
                }catch (BrokenBarrierException ex) {
                    return;
                }
            }
        }
    }

    public void start(){
        for(int i=0;i<workers.length;i++){
            new Thread(workers(i).start());
        }
        mainBoard.wartForCovergence();
    }
}
```
10. 另一种形式的栅栏是Exchange,它是一种`两方（Two-Party）栅栏`，各方在栅栏位置上交换数据。当`两方执行不对称的操作`时，Exchanger会非常有用，例如当一个线程想缓冲器写入数据，而另一个线程从缓冲区读取数据；这些线程可以使用Exchange来汇合，并将满的缓冲区与空的缓冲区交换。当两个线程通过Exchange交换对象时，这种交换就把两个对象安全的发布给另一方；
11. 数据交换的时机取决于应用程序的`响应需求`：
* 当缓冲区被填满时，由填充任务进行交换；
* 当缓冲区为空时，由清空任务进行交换；
* 这样会把需要`交换的次数`降到最低，但如果新数据的到达率不可预测时，那么一些数据的处理过程就将`延迟`，另一个方法是，不仅当缓冲区被填满时进行交换，并且当缓冲区被`填充到一定程度并保持一定时间`后，也进行交换；

# 构建高效且可伸缩的结果缓存
1. 几乎所有的应用程序都会使用某种形式的缓存。重用之前的计算结果能降低延迟，提高吞吐量，但却需要消耗更多的内存；
2. 缓存看上去非常简单，然而简单的缓存可能会将`性能瓶颈`变成`可伸缩性瓶颈`，即使缓存是用于提升线程的性能；
3. 本节将开发一个高效且可伸缩的缓存，用于改进一个高计算开销的函数；
4. 使用HashMap和同步机制来初始化缓存
```java
public interface Computable<A,V>{
    V compute(A arg)throws InterruptedException;
}

public class ExpensiveFunction implements Computable<String,BigInteger>{
    public BigInteger compute(String arg){
        //在经过长时间计算后
        return new BigInteger(arg);
    }
}

public class Memorizer1<A,V> implements Computable<A,V>{
    @GuardedBy("this")
    private final Map<A,V> cache =new HashMap<A,V>();
    private final Computable<A,V> c;

    public Memorizer1(Computable<A,V> c){
        this.c=c;
    }
    //由于HashMap非线程安全，对整个compute方法进行同步，这种方法能确保线程安全性，但会带来一个明显的可伸缩性问题：每次只有1个线程能执行compute
    //多线程并行compute时，可能会导致阻塞；使得compute的计算时间比没有Memorizer1的计算时间更长
    public synchronized V compute(A arg)throws InterruptedException{
        V result=cache.get(arg);
        if(result==null){
            result=c.compute(arg);
            cache.put(arg,result);
        }
        return result;
    }
}
```
5. 使用ConcurrentHashMap替换HashMap
```java
public class Memorizer2<A,V> implements Computable<A,V>{
    private final Map<A,V> cache =new ConcurrentHashMap<A,V>();
    private final Computable<A,V> c;

    public Memorizer2(Computable<A,V> c){
        this.c=c;
    }
    //由于ConcurrentHashMap线程安全，compute方法不需要同步，避免了并行compute时出现的串行访问情况；
    //但是当并行compute时存在一个安全漏洞，即可能会导致重复计算（如果计算开销很大，而其他线程不知道这个计算正在进行），并得到相同的值；
    public V compute(A arg)throws InterruptedException{
        V result=cache.get(arg);
        if(result==null){
            result=c.compute(arg);
            cache.put(arg,result);
        }
        return result;
    }
}
```
6. 基于FutureTask的Memorizing封装器 
引入FutureTask来处理`线程X正在计算`这种情况，FutureTask表示一个计算的过程，这个计算过程可能已经完成，也可能正在进行；如果有结果可用，那么FutureTask将立即返回，否则会一直阻塞直到结果计算出来再将其返回；
```java
public class Memorizer3<A,V> implements Computable<A,V>{
    //高效并发
    private final Map<A,V> cache =new ConcurrentHashMap<A,Future<V>>();
    private final Computable<A,V> c;

    public Memorizer3(Computable<A,V> c){
        this.c=c;
    }
    //由于ConcurrentHashMap线程安全，compute方法不需要同步，避免了并行compute时出现的串行访问情况；
    //但是当并行compute时存在一个安全漏洞，即可能会导致重复计算（如果计算开销很大，而其他线程不知道这个计算正在进行），并得到相同的值；
    public V compute(final A arg)throws InterruptedException{
        //缓存
        Future<V> f=cache.get(arg);
        //if为非原子操作（先检查再执行），仍然存在重复计算获得相同值的安全漏洞；
        //复合操作是在底层的Map对象上执行的，而这个对象无法通过加锁来确保原子性
        if(f==null){
            Callable eval=new Callable<V>(){
                public V call() throws InterruptedException{
                    return c.compute(arg);
                }
            }
            //将FutureTask作为缓存值
            FutureTask<V> ft=new FutureTask<V>(eval);
            f=ft;
            cache.put(arg,ft);
            //异步计算
            f.run();
        }
        try{
            //阻塞获取结果,或立即返回
            return f.get();
        }catch (ExecutionException e){
            throw launderThrowable(e.getCause);
        }
    }
}
```

7. Memorizer最终实现：复合操作的原子化
```java
public class Memorizer<A,V> implements Computable<A,V>{
    //高效并发
    private final ConcurrentMap<A,V> cache =new ConcurrentHashMap<A,Future<V>>();
    private final Computable<A,V> c;

    public Memorizer(Computable<A,V> c){
        this.c=c;
    }
    //由于ConcurrentHashMap线程安全，compute方法不需要同步，避免了并行compute时出现的串行访问情况；
    //但是当并行compute时存在一个安全漏洞，即可能会导致重复计算（如果计算开销很大，而其他线程不知道这个计算正在进行），并得到相同的值；
    public V compute(final A arg)throws InterruptedException{
        while true{
            //缓存
            Future<V> f=cache.get(arg);
            if(f==null){
                Callable eval=new Callable<V>(){
                    public V call() throws InterruptedException{
                        return c.compute(arg);
                    }
                }
                //将FutureTask作为缓存值
                FutureTask<V> ft=new FutureTask<V>(eval);
                //复合原子操作：若不存在则添加，否则get当前值
                f=cache.putIfAbsent(arg,ft);
                if(f==null){
                    f=ft;
                    //异步计算
                    f.run();
                }
            }
            try{
                //阻塞获取结果,或立即返回
                return f.get();
            }catch (CancellationException e){
                //如果某个计算被取消或失败，那么在计算这个结果时将指明计算过程被取消或者失败
                cache.remove(arg,f);
            }catch (ExecutionException e){
                throw launderThrowable(e.getCause);
            }
        }
    }
}
```
* 当缓存的是Future而不是值时，将导致`缓存污染`（Cache Pollution）问题；
* 本处未处理`缓存逾期`问题，`缓存清理`问题；
8. 在因式分解Servlet中使用Momorizer来缓存结果
```java
@ThreadSafe
public class Factorizer implements Servlet{

    private final Computable<BigInteger,BigInteger[]> c=new Computable<BigInteger,BigInteger[]>(){
        public BigInteger[] compute(BigInteger arg){
            return factor(arg);
        }
    }

    private final Computable<BigInteger,BigInteger[]> cache=new Memorizer<BigInteger,BigInteger[]>(c);
    
    public void service(ServletRequest req,ServletResponse resp){
        try{
            BigInteger i=extractFromRequest(req);
            encodeIntoResponse(resp,cache.compute(i));
        }catch(InterruptedException e){
            encodeError(resp,"factorization interrupted");
        }
    }
}
```



