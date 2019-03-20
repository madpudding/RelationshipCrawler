package com.qdcz.wanfang.plugin;


import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/*
* 读取每个分类的url文件
* 拿去url解析
* */
public class ClassifyUrl {

    public static List<String> getFiles(String path){
        //获得所有文件
        List<String> files=new ArrayList<>();
        File file=new File(path);
        File[] tempList=file.listFiles();
        for(int i=0;i<tempList.length;i++){
            if(tempList[i].isFile()){
                files.add(tempList[i].toString());
            }
        }
        return files;
    }

    public static void main(String args[]) throws Exception {
        //路径
        String path="D:/w/";
        List<String> fileList=getFiles(path);
        System.out.println(fileList.size());
        //设置编码格式
        String encoding="UTF-8";

        //依次读取
        for(int i=0;i<fileList.size();i++){
            //获取文件
            File file=new File(fileList.get(i));
            //获取文件长度
            Long fileLength=file.length();
            System.out.println(file.getName());
            byte[] filecontent=new byte[fileLength.intValue()];
            try {
                //读取
                FileInputStream in=new FileInputStream(file);
                in.read(filecontent);
                in.close();
            } catch (Exception e) {

            }
            String content=new String(filecontent,encoding);
            //按换行符切割
            String[] url=content.split("\n");
            ExecutorService threadPool=Executors.newFixedThreadPool(80);
            Set<Future<String>> set=new HashSet<>();
            //遍历得到每个url
            for (String u:url) {
                DealBigPage db=new DealBigPage(u);
                Future<String> future=threadPool.submit(db);
                set.add(future);
            }

            for(Future<String> future:set){
                String nc=future.get();
                //System.out.println(nc);
                boolean t=future.isDone();
                if(t==true){
                    threadPool.shutdown();
                }
            }
            //删除已操作完的文件
            file.delete();
            if(file.delete()==true){
                System.out.println("已完成一个文件的读取操作");
            }
        }
    }
}
