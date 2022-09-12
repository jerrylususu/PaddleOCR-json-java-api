package org.example;

import java.io.*;
import java.util.HashMap;
import java.util.Map;


public class Main {
    public static void main(String[] args) {
        // paddleocr_json 的可执行文件所在路径
        String exePath = "path/to/executable";

        // 可选的配置项
        Map<String, Object> arguments = new HashMap<>();
        // arguments.put("use_angle_cls", true);

        // 初始化 OCR
        try (PaddleOcrJson ocr = new PaddleOcrJson(new File(exePath), arguments)) {

            // 对一张图片进行 OCR
            String imgPath = "path/to/img";
            OcrResponse resp = ocr.runOcr(new File(imgPath));

            // 读取结果
            if (resp.code == OcrCode.OK) {
                for (OcrEntry entry : resp.data) {
                    System.out.println(entry.text);
                }
            } else {
                System.out.println("error: code=" + resp.code + " msg=" + resp.msg);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}