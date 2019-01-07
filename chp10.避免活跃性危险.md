避免活跃性危险
---
>在安全性与活跃性之间通常存在着某种制衡。我们使用加锁机制来确保线程安全，但如果过度使用加锁，则可能导致锁顺序死锁（Lock-Ordering DeadLock）。
>同样，我们使用线程池和信号量来限制对资源的使用，但这些限制的行为可能会导致资源死锁（Resource Deadlock）。
>Java应用程序无法从死锁中国恢复过来，因此在设计时一定要排除那些可能导致死锁出现的条件。
>本章将结束一些导致活跃性故障的原因，以及如何避免他们。

<!-- TOC -->

- [死锁](#死锁)
    - [锁顺序死锁](#锁顺序死锁)
    - [动态的锁顺序死锁](#动态的锁顺序死锁)
        - [在transferMoney中如何发生死锁？](#在transfermoney中如何发生死锁)
        - [在典型条件下会发生死锁的循环](#在典型条件下会发生死锁的循环)
    - [在协作对象之间发生的死锁](#在协作对象之间发生的死锁)
    - [开放调用](#开放调用)
    - [资源死锁](#资源死锁)
- [死锁的避免与诊断](#死锁的避免与诊断)
    - [支持定时的锁](#支持定时的锁)
    - [通过线程转储来分析死锁](#通过线程转储来分析死锁)
- [其他活跃性危险](#其他活跃性危险)
    - [饥饿](#饥饿)
    - [糟糕的响应性](#糟糕的响应性)
    - [活锁](#活锁)

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
## 开放调用
## 资源死锁

# 死锁的避免与诊断
## 支持定时的锁
## 通过线程转储来分析死锁
# 其他活跃性危险
## 饥饿
## 糟糕的响应性
## 活锁