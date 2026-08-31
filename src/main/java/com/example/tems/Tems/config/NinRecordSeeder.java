package com.example.tems.Tems.config;

import com.example.tems.Tems.model.NinRecord;
import com.example.tems.Tems.repository.NinRecordRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import java.time.LocalDate;

@Configuration
public class NinRecordSeeder {

    @org.springframework.context.annotation.Bean
    public CommandLineRunner seedNinRecords(NinRecordRepository repo) {
        return args -> {
            if (repo.count() > 0) return;

            NinRecord r1 = new NinRecord();
            r1.setNin("12345678901");
            r1.setFirstName("Adam");
            r1.setMiddleName("Chike");
            r1.setLastName("Bello");
            r1.setDateOfBirth(LocalDate.of(1994, 3, 12));
            r1.setStateOfOrigin("Kano");
            r1.setLga("Nassarawa");
            r1.setGender("MALE");
            r1.setAddress("14 Ahmadu Bello Way, Kano");
            repo.save(r1);

            NinRecord r2 = new NinRecord();
            r2.setNin("98765432109");
            r2.setFirstName("Amaka");
            r2.setMiddleName("Ifeoma");
            r2.setLastName("Okoro");
            r2.setDateOfBirth(LocalDate.of(1990, 7, 22));
            r2.setStateOfOrigin("Enugu");
            r2.setLga("Enugu East");
            r2.setGender("FEMALE");
            r2.setAddress("22 Zik Avenue, Enugu");
            repo.save(r2);

            System.out.println("✅ Seeded " + repo.count() + " dummy NIN records");
        };
    }
}