package com.portal.api.services;

import com.portal.api.model.MissionDialog;
import com.portal.api.repositories.MissionDialogRepository;
import org.springframework.stereotype.Service;

import javax.validation.Valid;
import java.util.List;
import java.util.Optional;

@Service
public class MissionDialogService {

    private final MissionDialogRepository missionRepository;

    public MissionDialogService(MissionDialogRepository missionRepository) {
        this.missionRepository = missionRepository;
    }

    public List<MissionDialog> getAllMissions() {
        return missionRepository.findAll();
    }

    public Optional<MissionDialog> getMissionById(String missionDailogId) {
        return missionRepository.findById(missionDailogId);
    }

    public MissionDialog updateMission(String missionId, @Valid MissionDialog mission) {
        mission.setMissionId(missionId);
        return missionRepository.save(mission);
    }
}
