package me.demo.java.concurrent.deadlock;

import java.util.Random;

/***
 * Java并发编程实战-10.1.2
 * 动态的锁顺序死锁-测试用例
 * @author wanggang
 * @date 2019/1/7
 */
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
