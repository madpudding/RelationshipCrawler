package com.qdcz.eupb.utils;


import com.qdcz.eupb.plugins.EupPostClassify;
import com.qdcz.eupb.plugins.EupbPostNum;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;

import java.io.File;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/*
* 读取分类xml
* 获取每个分类的具体数目
* 再解析
* */
public class EupbClassify {

    //读取xml,将结果存到List
    public Map<Integer,String> classifyNum() throws Exception {

        Map<Integer,String>  parameter=new HashMap<>();
        //读取文件
        Document doc=new SAXReader().read(new File("C:\\Users\\范\\Desktop\\技术学习\\ispider-master\\epub\\src\\main\\resources\\eupb.xml"));
        Element root=doc.getRootElement();
        Iterator it=root.elementIterator();

        while (it.hasNext()){
            Element foo=(Element)it.next();
            String classify=foo.elementText("classify");
            EupPostClassify ep=new EupPostClassify(classify);
            parameter.putAll(ep.call());
        }

        /*//创建线程池
        ExecutorService es=Executors.newFixedThreadPool(20);
        Set<Future<String>> set=new HashSet<>();

        //提交任务
        for (String cl:classfyList){
            EupPostClassify ep=new EupPostClassify(cl);
            Future<String> future=es.submit(ep);
            set.add(future);
        }*/

        /*//获取结果
        for ( Future<String> future:set) {
            future.get();
            System.out.println(future.get());
            boolean tt=future.isDone();
            if(tt==true){
                es.shutdown();
            }
        }*/

        //存取参数
        //若相等则添加
       /*if(result.size()==classfyList.size()){
            //循环添加
            for(int i=0;i<classfyList.size();i++){
                parameter.put(result.get(i),classfyList.get(i));
            }
        }*/
        System.out.println(parameter);
        //返回结果
        return parameter;
    }

    public static  void main(String args[]) throws Exception {
        //获取每个分类
        EupbClassify ep=new EupbClassify();
        Map<Integer,String> prameter=ep.classifyNum();
        //循环
        for(Map.Entry<Integer,String> pa:prameter.entrySet()){
            pa.getKey();//页数
            pa.getValue();//分类
            //获取页数
            int j= pa.getKey();
            //创建线程池
            ExecutorService es= Executors.newFixedThreadPool(80);
            Set<Future<Integer>> set=new HashSet<>();
            //提交任务
            for(int k=1;k<(j+1);k++){
                EupbPostNum epn=new EupbPostNum(k,pa.getValue());
                Future<Integer> future=es.submit(epn);
                set.add(future);
            }
            //获取结果
            for (Future<Integer> future:set) {
                Integer re=future.get();
                boolean tt=future.isDone();
                //若为真关闭线程池
                if(tt==true){
                    es.shutdown();
                }
            }
        }
    }

}
