package org.example;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;


public class Main {
    public static void main(String[] args) {
        // 可选的配置项
        Map<String, Object> arguments = new HashMap<>();
        // arguments.put("use_angle_cls", true);

        // 初始化 OCR：使用本地进程或者套接字服务器
        // 本地进程: new Ocr(new File(exePath), arguments)
        String exePath = "path_to_exe"; // paddleocr_json 的可执行文件所在路径
        try (Ocr ocr = new Ocr(new File(exePath), arguments)) {
//        使用套接字服务器
//        try (Ocr ocr = new Ocr(serverAddr, serverPort, arguments)) {



            // 对一张图片进行 OCR（使用路径）
            String imgPath = "path_to_img";
            OcrResponse resp = ocr.runOcr(new File(imgPath));

            // 或者使用图片数据（二进制或 base64）
//            byte[] fileBytes = Files.readAllBytes(Paths.get(imgPath));
//            OcrResponse resp = ocr.runOcrOnImgBytes(fileBytes);

//            OcrResponse resp = ocr.runOcrOnImgBase64("base64img");

            // 或者直接识别剪贴板中的图片
//             OcrResponse resp = ocr.runOcrOnClipboard();

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