package com.qdcz.wanfang.plugin;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class EssayUrl {

    public static void main(String args[]) throws Exception {
        //读取文件
        final int BUFFER_SIZE = 0x300000; // 缓冲区为3M
        File f = new File("D:\\万方/url.txt");
        int len = 0;
        Long start = System.currentTimeMillis();
        for (int z = 8; z > 0; z--) {
            MappedByteBuffer inputBuffer = new RandomAccessFile(f, "r")
                    .getChannel().map(FileChannel.MapMode.READ_ONLY,
                            f.length() * (z - 1) / 8, f.length() * 1 / 8);
            byte[] dst = new byte[BUFFER_SIZE];// 每次读出3M的内容
            List<String> st = new ArrayList<>();//开头url
            List<String> en = new ArrayList<>();//结尾url
            for (int offset = 0; offset < inputBuffer.capacity(); offset += BUFFER_SIZE) {
                if (inputBuffer.capacity() - offset >= BUFFER_SIZE) {
                    for (int i = 0; i < BUFFER_SIZE; i++)
                        dst[i] = inputBuffer.get(offset + i);
                } else {
                    for (int i = 0; i < inputBuffer.capacity() - offset; i++)
                        dst[i] = inputBuffer.get(offset + i);
                }
                int length = (inputBuffer.capacity() % BUFFER_SIZE == 0) ? BUFFER_SIZE
                        : inputBuffer.capacity() % BUFFER_SIZE;
                len += new String(dst, 0, length).length();
                System.out.println(new String(dst, 0, length).length() + "-" + (z - 1) + "-" + (8 - z + 1));
                String s = new String(dst);
                String ys = s.replaceAll("aspx", "aspx,");
                String[] urls = ys.split(",");
                List<String> ul = new ArrayList<>();//总的url
                //循环添加
                for (String url : urls) {
                    ul.add(url);
                    //不完整则移除
                    //结尾不完整
                    if (!url.endsWith(".aspx") && url.startsWith("htt")) {
                        ul.remove(url);
                        st.add(url);
                    }
                    //开头不完整
                    if (!url.startsWith("htt") && url.endsWith(".aspx")) {
                        ul.remove(url);
                        en.add(url);
                    }

                }
                //若相等则添加
                if (st.size() == en.size()) {
                    for (int i = 0; i < st.size(); i++) {
                        //组合后，移除已组合的url
                        ul.add(st.get(i) + en.get(i));
                        st.remove(i);
                        en.remove(i);
                    }
                }

                //创建线程池
                ExecutorService threadPool = Executors.newFixedThreadPool(100);
                Set<Future<String>> set = new HashSet<>();

                //循环提交任务
                for (final String url : ul) {
                    DealOnePage task = new DealOnePage(url);
                    Future<String> future = threadPool.submit(task);
                    set.add(future);
                }

                for (Future<String> future : set) {
                    String n = future.get();
                    System.out.println(n);
                    //判断是否完成若完成则关闭
                    boolean t = future.isDone();
                    if (t == true) {
                        threadPool.shutdown();
                    }
                }
            }
        }
    }
}



