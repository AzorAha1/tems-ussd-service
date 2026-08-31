package com.example.tems.Tems.service;

import com.example.tems.Tems.model.NinRecord;
import java.util.Optional;

public interface NinLookupService {
    Optional<NinRecord> lookupByNin(String nin);
}