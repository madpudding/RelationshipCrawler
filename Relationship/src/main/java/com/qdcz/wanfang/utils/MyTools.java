package com.qdcz.wanfang.utils;

import com.qdcz.spider.download.HttpDownloader;
import com.qdcz.spider.download.URLConnDownloader;
import com.qdcz.spider.http.HttpBuilder;
import com.qdcz.spider.http.Request;
import com.qdcz.spider.http.Response;
import com.qdcz.spider.proxy.MyProxyAuthenticator;
import com.qdcz.spider.utils.Tools;

import java.io.File;
import java.io.FileWriter;
import java.net.Authenticator;
import java.util.Vector;
import java.util.concurrent.Callable;

public class MyTools implements Callable {

    private String url;

    public MyTools( String u){
        url=u;
    }

    private static HttpDownloader downloader = new URLConnDownloader();

    public static byte[] getDownload(String url){
        byte[] data = null;
        Request request = new Request(url,Request.Method.GET);
        request.setRead_time_out(90000);
        request.setConn_time_out(60000);
        request.setHeaderField("http-pro.abuyun.com", 9010);
        request.setHeaderField("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8");
        request.setHeaderField("Accept-Encoding", "gzip, deflate");
        request.setHeaderField("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
        request.setHeaderField("Connection","keep-alive");
        request.setHeaderField("Host","wanfang.jslib.org.cn:8088");
        request.setHeaderField("Upgrade-Insecure-Requests","1");
        request.setHeaderField("User-Agent","Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/67.0.3396.99 Safari/537.36");
        Vector<String> urls=new Vector<>();
        HttpBuilder.buildRequest(request);
        int count = 5;
        while(count!=0){
            try {
                Response respone = downloader.download(request);
                data = respone.getBody();
                Tools.getMultiResultsByOneXpathPattern(data,"UTF-8","//*[@class='list_ul']/li[1]/a[1]/@href",urls);
                System.out.println(urls.size());
                //写入文件
                FileWriter fw=null;
                File files=new File("D:/wanfangUrls.txt");

                StringBuffer sb=new StringBuffer();
                //不存在则创建

                //循环写入
                synchronized (""){
                    for(int i=0;i<urls.size();i++){
                        sb.append(urls.get(i)+"\n");
                    }
                    fw=new FileWriter(files,true);
                    fw.append(sb);
                    fw.flush();
                    fw.close();
                }
                if(data==null){
                    count--;
                }else{
                    break;
                }
                Thread.sleep(10*1000);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return null;
    }
    @Override
    public String call() {
        Authenticator.setDefault(new MyProxyAuthenticator("H10X69817796RM1P", "4E8D11AE8FF5F103"));
        getDownload(url);
        return null;
    }

/*
    public static void main(String[] args) {
        Authenticator.setDefault(new MyProxyAuthenticator("H10X69817796RM1P", "4E8D11AE8FF5F103"));
        String url = "http://lib-wf.sstir.cn/S/paper.aspx?q=clcnumber%3aT+%e5%88%86%e7%b1%bb%e5%8f%b7%3a%22TM*%22+DBID%3aWF_QK&o=sortby+CitedCount+CoreRank+date/weight=5++relevance&f=sort&=10&p=4";
        getDownload(url);
    }

    public static String replaceStr(String str){
        return str.replace("\\s+", "").replace("&nbsp;", "").replace("\n", "").replace("\r", "").trim();
    }*/
}
