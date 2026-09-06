package com.premiumreminder;

import com.premiumreminder.service.BirthdayCardService;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class BirthdayCardServiceRunner {

    public static void main(String[] args) throws IOException {
        String name = args.length > 0 ? args[0] : "Sanjib Sadhukhan";
        String dob = args.length > 1 ? args[1] : "11-08-2019";

        String downloadsDir = System.getProperty("user.home") + File.separator + "Downloads";
        String fileName = "birthday_card_" + name.replaceAll("\\s+", "_") + ".png";
        String outputPath = args.length > 2 ? args[2] : downloadsDir + File.separator + fileName;

        // Ensure the Downloads folder exists (it normally does, but just in case)
        new File(downloadsDir).mkdirs();

        BirthdayCardService service = new BirthdayCardService();
        byte[] cardBytes = service.generateCard(name, dob);

        try (FileOutputStream fos = new FileOutputStream(outputPath)) {
            fos.write(cardBytes);
        }

        System.out.println("Saved: " + new File(outputPath).getAbsolutePath());
    }
}