避免活跃性危险
---
>在安全性与活跃性之间通常存在着某种制衡。我们使用加锁机制来确保线程安全，但如果过度使用加锁，则可能导致`锁顺序死锁`（Lock-Ordering DeadLock）。  
>同样，我们使用线程池和信号量来限制对资源的使用，但这些限制的行为可能会导致`资源死锁`（Resource Deadlock）。  
>Java应用程序无法从死锁中恢复过来，因此在设计时一定要排除那些可能导致死锁出现的条件。  
>本章将结束一些导致活跃性故障的原因，以及如何避免他们。  

<!-- TOC -->

- [死锁](#死锁)
    - [锁顺序死锁](#锁顺序死锁)
    - [动态的锁顺序死锁](#动态的锁顺序死锁)
        - [在transferMoney中如何发生死锁？](#在transfermoney中如何发生死锁)
        - [在典型条件下会发生死锁的循环](#在典型条件下会发生死锁的循环)
    - [在协作对象之间发生的死锁](#在协作对象之间发生的死锁)
    - [开放调用（Open Call）](#开放调用open-call)
    - [资源死锁](#资源死锁)
- [死锁的避免与诊断](#死锁的避免与诊断)
    - [支持定时的锁](#支持定时的锁)
    - [通过线程转储来分析死锁](#通过线程转储来分析死锁)
- [其他活跃性危险](#其他活跃性危险)
    - [饥饿(Starvation)](#饥饿starvation)
    - [丢失信号(糟糕的响应性)](#丢失信号糟糕的响应性)
    - [活锁(LiveLock)](#活锁livelock)
- [小结](#小结)

<!-- /TOC -->
# 死锁
>哲学家进餐问题：5个哲学家吃中餐，只有5双筷子，每2个人中间放1双筷子，哲学家时而思考，时而进餐。每个人都需要1双筷子才能进餐，并且进餐后将筷子放回原处继续思考。
1. 有些筷子管理算法能让每个人都能相对及时的吃到东西：如1个饥饿的哲学家会尝试获得相邻的2根筷子，但如果其中1根筷子正在被另一个哲学家使用，那么他将放弃这根筷子等待几分钟后再次尝试；
2. 有些算法会导致一些或者所有的哲学家都饿死：每个人都同时抓住自己左边的筷子，然后等待自己右边的筷子空出来，但同时又不放下已经拿到的筷子；
3. 这种情况将产生`死锁`:每个人都拥有其他人需要的资源，同时又在等待其他人已经拥有的资源，并且每个人在获得所有需要的资源之前都不会放弃已经拥有的资源；
4. 当一个线程永远的持有一个锁，并且其他线程都在尝试获得这个锁时，那么它们将永远被阻塞。
5. 在线程A持有锁L并想获得锁M的同时，线程B持有锁M并尝试获得锁L，那么这两个线程将永远的等待下去；这就是最简单的死锁形式，抱死（Deadly Embrace）；
6. 其中多个线程由于存在`环路的锁依赖关系`而永远的等待下去（把每个`线程`假想为`有向图`中的一个`节点`，图中的每条`边`表示的关系是`线程A等待线程B所占有的资源`，如果在图中形成了一条环路，那么久存在一个死锁）；
7. 在数据库系统的设计中考虑了监测死锁以及从死锁中恢复；在执行一个事务时，需要获取锁并且一直持有这些锁直到事务提交，因此在2个事务之间很可能发生死锁，当发生死锁时，数据库服务器检测到一组事务发生死锁（通过在表示等待关系的有向图找哪个搜索循环），将选择一个牺牲者并放弃这个事务，作为牺牲者的事务会释放它所持有的资源，从而使其他事务继续进行。应用程序可以重新执行被强制中止的事务，而这个事务现在可以成功完成，因为所有跟它竞争资源的事务都已经完成了；
8. JVM在解决死锁问题是没有数据库服务那么强大。当一组Java线程发生死锁时，这些线程永远就不能再使用了，唯一的恢复方式就是中止或重启它；
9. 当死锁出现时，往往是最糟糕的时候-在高负载情况下；
## 锁顺序死锁
1. A和B两个线程交错执行将会发生死锁
```java
//A：--锁住left-->尝试锁住right-->永久等待
//B：----锁住right-->锁住left-->永久等待
public class LeftRightDeadlock{
    private final Object left=new Object();
    private final Object right=new Object();

    private void leftRight(){
        synchronized(left){
            synchronized(right){
                doSth();
            }
        }
    }

    private void rightLeft(){
        synchronized(right){
            synchronized(left){
                doSth();
            }
        }
    }
}
```
死锁原因：两个线程试图以`不同的顺序`来获得相同的锁。如果按照相同的顺序来请求锁，那么就不会出现`循环的加锁依赖性`，因此也就不会出现死锁；
2. 如果所有线程都以固定的顺序来获得锁，那么在程序中就不会出现`顺序死锁`问题。

## 动态的锁顺序死锁
```java
public void transferMoney(Account fromAccount,Account toAccount,DollarAmount amount) throws InsufficientFundsException{
    synchronized(fromAccount){
        synchronized(toAccount){
            if(fromAccount.getBalance().compareTo(account)<0){
                throws new InsufficientFundsException();
            }else{
                //扣款
                fromAccount.debit(amount);
                //打款
                toAccount.credit(amount);
            }
        }
    }
}
```
### 在transferMoney中如何发生死锁？
>A：transferMoney(myAccount,yourAccount,10)
>B：transferMoney(yourAccount,myAccount,20)
1. 如果执行顺序不当，那么A可能获得myAccount的锁并等待yourAccount的锁；然而B此时持有yourAccount的锁，并正在等待myAccount的锁；
2. 如果存在嵌套的锁获取操作，由于我们无法控制参数的顺序，因此要解决这个问题，必须定义锁的顺序，并且在整个应用程序中都按照这个顺序来获取锁；
3. 在制定锁的顺序时，可以使用System.identityHashCode方法，该方法将返回由Object.hashCode返回的值；
```java
  private static final Object tieLock=new Object();
  public static void transferMoney(final Account fromAccount, final Account toAccount, final DollarAmount amount) throws InsufficientFundsException {
        class Helper {
            public void transfer() throws InsufficientFundsException {
                if (fromAccount.getBalance().compareTo(toAccount.getBalance()) < 0) {
                    throw new InsufficientFundsException();
                } else {
                    //扣款
                    fromAccount.debit(amount);
                    //打款
                    toAccount.credit(amount);
                    System.out.println(String.format("Account_%s debit:%s, balance:%s", fromAccount.getAcctNo(), amount.getAmount(), fromAccount.getBalance().getAmount()));
                    System.out.println(String.format("Account_%s credit:%s, balance:%s", toAccount.getAcctNo(), amount.getAmount(), toAccount.getBalance().getAmount()));
                }
            }
        }

        int fromHash = System.identityHashCode(fromAccount);
        int toHash = System.identityHashCode(toAccount);
        if (fromHash < toHash) {
            synchronized (fromAccount) {
                synchronized (toAccount) {
                    new Helper().transfer();
                }
            }
        } else if (fromHash > toHash) {
            synchronized (toAccount) {
                synchronized (fromAccount) {
                    new Helper().transfer();
                }
            }
        } else {
            //在极少数情况下，两个对象可能拥有相同的hashCode，此时必须通过任意的方法来决定锁的顺序，而这可能又会重新引入死锁。为了解决这种情况，可以使用`加时赛`（Tie-Breaking）锁；
            synchronized (tieLock) {
                synchronized (fromAccount) {
                    synchronized (toAccount) {
                        new Helper().transfer();
                    }
                }
            }
        }
    }
```
### 在典型条件下会发生死锁的循环
DemostrateDeadlock在大多数系统下都会很快发生死锁
```java
package me.demo.java.concurrent.deadlock;

import java.util.Random;

public class TransferMoneyNoOrder {
    private static final int NUM_THREADS = 20;
    private static final int NUM_ACCOUNTS = 5;
    private static final int NUM_ITERATIONS = 1000000;

    public static void main(String[] args) throws InterruptedException {
        final Random rnd = new Random();
        final Account[] accounts = new Account[NUM_ACCOUNTS];

        for (int i = 0; i < accounts.length; i++) {
            accounts[i] = new Account(i, new DollarAmount(10000));
        }

        class TransferThread extends Thread {

            @Override
            public void run() {
                for (int i = 0; i < NUM_ITERATIONS; i++) {
                    int fromAccount = rnd.nextInt(NUM_ACCOUNTS);
                    int toAccount = rnd.nextInt(NUM_ACCOUNTS);
                    DollarAmount amount = new DollarAmount(rnd.nextInt(1000));
                    try {
                        transferMoney(accounts[fromAccount], accounts[toAccount], amount);
                    } catch (InsufficientFundsException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        for (int i = 0; i < NUM_THREADS; i++) {
            new TransferThread().start();
        }
        Thread.currentThread().join();
    }

    interface Amount {
    }

    static class DollarAmount implements Amount, Comparable {
        long amount;

        public DollarAmount(int amt) {
            amount = amt;
        }

        long getAmount() {
            return amount;
        }

        public void setAmount(long amount) {
            this.amount = amount;
        }

        public int compareTo(Object o) {
            return this.getAmount() > ((DollarAmount) o).getAmount() ? 1 : 0;
        }
    }

    static class InsufficientFundsException extends Exception {
    }

    static class Account {
        int accountId;
        DollarAmount balance;

        public Account(int accountId, DollarAmount balance) {
            this.accountId = accountId;
            this.balance = balance;
        }

        void debit(DollarAmount d) {
            balance.setAmount(balance.getAmount() - d.getAmount());
        }

        void credit(DollarAmount d) {
            balance.setAmount(balance.getAmount() + d.getAmount());
        }

        DollarAmount getBalance() {
            return balance;
        }

        int getAcctNo() {
            return accountId;
        }
    }

    public static void transferMoney(Account fromAccount, Account toAccount, DollarAmount amount) throws InsufficientFundsException {
        synchronized (fromAccount) {
            synchronized (toAccount) {
                if (fromAccount.getBalance().compareTo(toAccount.getBalance()) < 0) {
                    throw new InsufficientFundsException();
                } else {
                    //扣款
                    fromAccount.debit(amount);
                    //打款
                    toAccount.credit(amount);
                    System.out.println(String.format("Account_%s debit:%s, balance:%s", fromAccount.getAcctNo(), amount.getAmount(), fromAccount.getBalance().getAmount()));
                    System.out.println(String.format("Account_%s credit:%s, balance:%s", toAccount.getAcctNo(), amount.getAmount(), toAccount.getBalance().getAmount()));
                }
            }
        }
    }
}
```

## 在协作对象之间发生的死锁
某些获取多个锁的操作并不像在LeftRightDeadlock或transferMonet中那么明显，这两个锁并不一定必须在同一个方法中被获取。

```java 
/*在相互协作对象之间的锁顺序死锁（不要这么做）*/
//两个相互协作的类，在出租车调度系统中可能会用到它们
//Taxi代表一个出租车对象，包含位置和目的地两个属性
class Taxi{
    @GuardBy("this") private Point location,destination;
    private final Dispatcher dispatcher;

    public Taxi(Dispatcher dispatcher){
        this.dispatcher=dispatcher;
    }

    public synchronized Point getLocation(){
        return location;
    }

    public synchronized void setLocation(Point location){
        //Taxi先获取Taxi的锁，再获取Dispatcher的锁
        this.location=location;
        if(location.equals(destination)){
            dispatcher.notifyAvaliable(this);
        }
    }
}

//Dispatcher代表一个出租车车队
class Dispatcher{
    @GuardBy("this") private final Set<Taxi> taxis;
    @GuardBy("this") private final Set<Taxi> availableTaxis;

    public Dispatcher(){
        taxis=new HashSet<Taxi>();
        availableTaxis=new HashSet<Taxi>();
    }

    public synchronized void notifyAvaliable(Taxi taxi){
        availableTaxis.add(taxi);
    }

    public synchronized Image getImage(){
        Image image=new Image();
        //Dispatcher先获取Dispatcher的锁，再获取Taxi的锁
        for(Taxi taxi:taxis){
            image.drawMarker(t.getLocation());
        }
        return image;
    }
}
```
如果在持有锁的情况下调用某个外部方法，那么久需要警惕死锁：因为这个外部方法可能会获取其他锁，而这可能会产生死锁，或者阻塞时间过长，导致其他线程无法及时获得当前被持有的锁；

## 开放调用（Open Call）
1. 如果调用某个方法时`不需要持有锁`，那么这种调用被称为开放调用；
2. 依赖开放调用的类通常能表现出更好的行为，并且与那些在调用方法时需要持有锁的类相比，也更`容易编写`；
3. 在程序中应尽量使用开放调用。与那些在持有锁时调用外部方法的程序相比，更易于对依赖于开放调用的程序进行`死锁分析`；

```java
/*通过公开调用来避免在相互协作的对象之间产生死锁*/
@ThreadSafe
class Taxi{
    @GuardBy("this") private Point location,destination;
    private final Dispatcher dispatcher;

    public Taxi(Dispatcher dispatcher){
        this.dispatcher=dispatcher;
    }

    public synchronized Point getLocation(){
        return location;
    }

    //开放调用，消除死锁风险
    public void setLocation(Point location){
        boolean reachedDestination;
        //收缩同步代码块的保护范围可以提高伸缩性
        //更新位置和通知出租车空闲状态可不必是原子操作
        synchronized(this){
            this.location=location;
            reachedDestination=location.equals(destination);
        }
        if(reachedDestination){
            dispatcher.notifyAvaliable(this);
        }
    }
}

@ThreadSafe
class Dispatcher{
    @GuardBy("this") private final Set<Taxi> taxis;
    @GuardBy("this") private final Set<Taxi> availableTaxis;

    public Dispatcher(){
        taxis=new HashSet<Taxi>();
        availableTaxis=new HashSet<Taxi>();
    }

    public synchronized void notifyAvaliable(Taxi taxi){
        availableTaxis.add(taxi);
    }

    //开放调用，消除死锁风险
    public  Image getImage(){
        Set<Taxi> copy;
        //同步块仅用于保护那些涉及共享状态的操作
        synchronized(this){
            copy=new HashSet<Taxi>(taxis);
        }
        Image image=new Image();
        for(Taxi taxi:copy){
            image.drawMarker(t.getLocation());
        }
        return image;
    }
}
```

## 资源死锁
1. 正如当多个线程相互持有彼此正在等待的锁而又不释放自己已持有的锁时会发生死锁，当它们`在相同的资源集合上等待时，也会发生死锁`；
2. 例如2个资源池，两个不同的`数据库连接池`，资源池通常采用信号量实现当资源池为空时的阻塞行为。如果一个任务需要连接2个数据库，并且在请求2个资源时不会遵循相同的顺序，那么线程A可能持有有数据库D1的连接，并等待与数据库D2的连接，而线程B则持有数据库D2的连接并等待数据库D1的连接；
3. 资源池越大，出现这种情况的可能性越小，如果每个资源池都有N个连接，那么发生死锁时不仅需要N个循环等待的线程，而且还需要大量不恰当的执行时序；
4. 另一种基于资源的死锁形式就是`线程饥饿死锁`（Thread-Starvation Deadlock）；
5. 线程饥饿死锁示例：`一个任务提交另一个任务`，并且等待被提交的任务在`单线程的Executor`中执行完成。在这种情况下，第一个任务将永远等待下去，并使得另一个任务以及在这个Executor中执行的所有其他任务都停止执行。
6. 如果`某些任务需要等待其他任务的结果`，那么这些任务往往是产生饥饿死锁的主要来源，`有界线程池`/`资源池`/与`相互依赖的任务`不能一起使用。

# 死锁的避免与诊断
1. 如果必须获取多个锁，那么在设计时必须考虑`锁的顺序`：尽量减少潜在的`加锁交互数量`，将`获取锁时需要遵循的协议`写入正式文档并始终遵循这些协议；
2. 在使用细粒度锁的程序中，可以通过使用一种`两阶段策略（Two-Part Strategy）`来检查代码中的死锁：
   1. 找出`在什么地方将获取多个锁`（使这个集合尽量小）；
   2. 对所有这些实例进行`全局分析`，从而确保他们在整个程序中获取锁的顺序都保持一致；
   3. 尽可能地使用`开放调用`，这能极大地简化分析过程；
   4. 如果所有的调用都是开放调用，那么要发现获取多个锁的实例是非常简单的，可以通过`代码审查`，或者借助`自动化的源代码分析工具`；

## 支持定时的锁
1. 使用内置锁时，只要没有获得锁，就会永远的等待下去；
2. `显式锁`可以指定一个`超时时限`（Timeout），在等待超过该时间后tryLock会返回一个失败信息；
3. 即使在整个系统中没有始终使用定时锁，使用定时锁来获取多个锁也能有效地应对死锁问题；
4. 如果在获取锁时超时，那么可以释放这个锁，然后后退并`在一段时间后再次尝试`，从而消除了死锁发生的条件，使程序恢复过来；
5. 但这项技术只有在`同时获取2个锁时才有效`，如果在`嵌套`的方法调用中请求多个锁，那么即使你知道已经持有了外层的锁，也无法释放它。

## 通过线程转储来分析死锁
1. JVM可以通过线程转储（Thread Dump）来帮助识别死锁的发生；
2. 线程转储包含各个运行中的线程的栈追踪信息，这类似于发生异常时的栈追踪信息；
3. 线程转储还包含加锁信息，例如每个线程持有那些锁，在哪些栈帧中获得这些锁，以及被阻塞的线程正在等待获取哪一个锁；
4. 在生成线程转储之前，JVM将在`等待关系图`中通过`搜索循环`来找出`死锁`。如果发现一个死锁，则获取相应的死锁信息，例如在死锁中涉及哪些锁和线程，以及这个锁的获取操作位于程序的哪些位置；
5. 线程转储中不包含`显式的Lock`;
6. `JDBC`规范中并没有要求`Connection`必须是线程安全的，以及Connection通常被封闭在单个线程中使用；如果在2个线程中在并发使用同一个Connection，就可能会产生死锁；

# 其他活跃性危险
## 饥饿(Starvation)
1. 当线程由于无法访问它所需要的资源而不能继续执行时，就发生`了饥饿（Staarvation）`;
2. 引发饥饿的最常见资源就是`CPU时钟周期`；
3. 如果在Java应用程序中对线程的`优先级`使用不当，或者在持有锁时执行一些无法结束的结构（例如无限循环，或者无限制的等待某个资源），那么也可能导致饥饿，因为其他需要这个锁的线程将无法得到它；
4. 在`Thread API`中定义的线程优先级只是作为线程调度的参考，在Thread API中定义了`10个优先级`，JVM根据需要将他们映射到`操作系统的调度优先级`，这个映射是与特定平台相关的；

## 丢失信号(糟糕的响应性)
1. 如果在GUI应用程序中使用了后台线程，糟糕的响应性是很常见的；
2. `CPU密集型`的后台任务仍然可能对`响应性`造成影响，因为它们会与事件线程共同竞争CPU的时钟周期。这个时候就可以发挥线程优先级的作用，此时计算密集型的后台任务将对响应性造成影响。如果由其他线程完成的工作都是后台任务，那么应该`降低他们的优先级`，从而提高前台程序的响应性；
3. `不良的锁管理`也可能导致糟糕的响应性，如果某个线程长时间占有一个所，而其他想要访问这个容器的线程就必须等待很长时间；

## 活锁(LiveLock)
1. 活锁是另一种形式的活跃性问题，该问题尽管不会阻塞线程，但也不能继续执行，因为线程将不断重复执行相同的操作，而且总会失败；
2. 活锁通常发生在处理事务消息的应用程序中：如果不能成功的处理某个消息，那么消息处理机制将回滚整个事务，并将它重新放到队列的开头，因此处理器将被反复调用，并返回相同的结果（有时候也被称为Poison Message）；
3. 当多个`相互协作的线程`都对彼此进行响应从而修改各自的状态，并使得任何一个线程都无法继续执行时，就发生了活锁
    >就像2个过于礼貌的人在半路上面对面的相遇，他们彼此都让出对方的路，然而又在另一条路上相遇了，因此他们就这样反复的避让下去；‘——’
4. 要解决这种活锁问题，需要在重试机制中引入随机性，在并发应用程序中，通过等待随机长度的时间和回退可以有效的避免活锁的发生；

# 小结
1. 活跃性故障是一个非常严重的问题，因为当出现`活跃性故障`时，除了中止应用程序之外没有其他任何机制可以帮助从这种故障中恢复过来；
2. 最常见的活跃性故障就是`锁顺序死锁`，在设计时应该避免产生锁顺序死锁，确保线程在获取多个锁时采用`一致的顺序`；
3. 最好的解决方案是在程序中始终使用`开放调用`，这将大大减少需要同时持有多个锁的地方，也更容易发现这些地方；
