package com.qdcz.wanfang.utils;

import com.qdcz.spider.utils.Tools;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;


public class WanFangClassifyTrue {
    /*
     * 读取xml文件
     * 请求获取每个分类的所有url集合
     * throws Exception
     * */
    public static List<String> getUrl() throws Exception {
        //读取XML文档
        Document doc=new SAXReader().read(new File("D:\\IdeaWorkSpace\\Wanfang\\src\\main\\resources/classify1.xml"));
        Element root=doc.getRootElement();
        Iterator it=root.elementIterator();

        Vector<String> contents=new Vector<>();//内容url集合
        List<String> name=new ArrayList<>();//分类名集合
        int essay=0;//总共页数
        int pNum=0;//读取页数

        //循环获得每个分类的url集合
        while (it.hasNext()){
            Element foo=(Element) it.next();
            String classify=foo.elementText("classify");
            String url=foo.elementText("url");
            String Accept=foo.elementText("Accept");
            String Accept_Encoding=foo.elementText("Accept_Encoding");
            String Accept_Language=foo.elementText("Acccept_Language");
            String Host=foo.elementText("Host");
            String Connection=foo.elementText("Connection");
            String Referer=foo.elementText("Referer");
            String Upgrade=foo.elementText("Upgrade_Insecure_Requests");
            String User_Agent=foo.elementText("User_Agent");
            String q=foo.elementText("q");
            String f=foo.elementText("f");
            String p=foo.elementText("p");
            String number=foo.elementText("number");
            String number_xpath=foo.elementText("number_xpath");
            HttpGet get=new HttpGet(url+q+f);
            name.add(classify);
            try {
                //设置请求头
                get.setHeader("Accept", Accept);
                get.setHeader("Accept-Encoding", Accept_Encoding);
                get.setHeader("Accept-Language", Accept_Language);
                get.setHeader("Connection", Connection);
                get.setHeader("Host", Host);
                get.setHeader("Referer", Referer);
                get.setHeader("Upgrade-Insecure-Requests", Upgrade);
                get.setHeader("User-Agent", User_Agent);
                CloseableHttpClient httpClient = HttpClientBuilder.create().build();
                //进行post请求
                HttpResponse response = httpClient.execute(get);
                //状态码不对报错
                if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                    //以字节形式承载内容
                    String content=EntityUtils.toString(response.getEntity());
                    //System.out.println(content);
                    byte[] data=content.getBytes();
                    String num=Tools.get_one_item(data,"UTF-8",number_xpath);
                    String ns=num.replaceAll("\\s*", "").replaceAll("找到","").replaceAll("篇论文","").replaceAll(",","");
                    System.out.println(ns);
                    //获得总文章数
                    essay=Integer.valueOf(ns).intValue();
                    //获得需要爬取的篇数
                    int origin=Integer.valueOf(number).intValue();
                    int now=essay-origin;
                    double d;
                    d = now;
                    double lastId = Math.ceil(d/10);
                    //获得需要爬取的页数
                    pNum = Integer.parseInt(new java.text.DecimalFormat("0").format(lastId));
                }
                //关闭内容流
                EntityUtils.consume(response.getEntity());
                //释放连接
                httpClient.close();
            }
            catch (Exception e) {
                Thread.sleep(1000);
            }
            File files=new File("D:/w/"+classify+".txt");
            FileOutputStream fo=null;
            if(!files.exists()){
                files.createNewFile();
            }
            for(int i=1;i<(pNum+1);i++){
                String urlc=url+q+f+p+i+"\n";
                //System.out.println(urlc);
                fo=new FileOutputStream(files,true);
                fo.write(urlc.getBytes());
                fo.flush();
            }
            fo.close();

            System.out.println(pNum);
        }
        return name;
    }



    public static void main(String args[]) throws Exception {
        List<String> name =getUrl();
        System.out.println(name);
    }
}
