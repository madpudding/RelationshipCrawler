package com.qdcz.cnki.util;

import com.qdcz.cnki.plugins.DealOnePage;
import com.qdcz.cnki.plugins.DealUniversty;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/*
*
* 处理中国知网总控制
* */
public class Utils {

    /*
    * 获取大学url
    * */
    public static List<String> dealUniversty(){
        String url="http://navi.cnki.net/knavi/Common/Search/PPaper";
        List<String> urls=new ArrayList<>();
        //提交任务
        for(int k=1;k<40;k++){
            DealUniversty du=new DealUniversty(url,k);
            List<String> ul=du.call();
            for(int i=0;i<ul.size();i++){
                urls.add(ul.get(i));
            }
        }
        //System.out.println(urls.size()+"801则正确");
        //返回大学url
        return urls;
    }

    //获取大学url，加入多线程解析
    public static void getMessage(){
        List<String> unUrl=dealUniversty();
        System.out.println(unUrl.size());
        ExecutorService threadPool=Executors.newFixedThreadPool(24);
        Set<Future<String>> set=new HashSet<>();

        //循环提交任务
        for (final String url:unUrl) {
            DealOnePage task=new DealOnePage(url);
            Future<String> future=threadPool.submit(task);
            set.add(future);
        }

        //得到返回结果
        for (Future<String> future:set){
            try {
                String n=future.get();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException e) {
                e.printStackTrace();
            }
            boolean t=future.isDone();
            //为真，关闭线程池的所有线程
            if(t==true){
                threadPool.shutdown();
            }
        }

    }



    public static void main (String args[]){
        getMessage();
    }
}
