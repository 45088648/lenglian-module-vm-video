package com.beetech.module.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class TestCmd {

    public static void main(String[] args) throws Exception {
//      String cmd = "adb shell lsof -p 26697";
        String cmd = "adb shell ls -l /proc/2489/fd";
        execmd(cmd);
    }

    public static void execmd(String cmd) throws Exception{
        //此方法为打印日志到控制台！！！！！！！！！！！！
        //此方法跑成功！！！

        System.out.println("在cmd里面输入" + cmd);
        Process p;
        BufferedReader bReader = null;
        InputStreamReader inputStreamReader = null;
        InputStream in = null;
        try {
            p = Runtime.getRuntime().exec(cmd);
            System.out.println(":::::::::::::::::::开始在控制台打印日志::::::::::::::::::::::>>>>>>");
            //p.waitFor();
            in = p.getInputStream();
            inputStreamReader = new InputStreamReader(in, "UTF-8");
            bReader = new BufferedReader(inputStreamReader);
            String line = null;
            int lineNum = 1;
            while ((line = bReader.readLine()) != null) {
                if("".equals(line)) continue;
                System.out.println(lineNum++ + ":" + line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            bReader.close();
            inputStreamReader.close();
            in.close();
        }
    }
}
