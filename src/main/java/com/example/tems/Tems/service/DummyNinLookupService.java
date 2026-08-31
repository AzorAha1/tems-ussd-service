package com.example.tems.Tems.service;

import com.example.tems.Tems.model.NinRecord;
import com.example.tems.Tems.repository.NinRecordRepository;
import org.springframework.stereotype.Service;
import java.util.Optional;

@Service
public class DummyNinLookupService implements NinLookupService {

    private final NinRecordRepository ninRecordRepository;

    public DummyNinLookupService(NinRecordRepository ninRecordRepository) {
        this.ninRecordRepository = ninRecordRepository;
    }

    @Override
    public Optional<NinRecord> lookupByNin(String nin) {
        return ninRecordRepository.findByNin(nin);
    }
}