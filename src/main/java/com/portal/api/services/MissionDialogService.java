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

    public void setNamePlaceholder(MissionDialog missionDialog, String namePlaceholder, String firstName) {
        fillPlaceholder(missionDialog, namePlaceholder, firstName);
    }


    public void setPowerOnePlaceholder(MissionDialog missionDialog, String powerPlaceholder1, int percentage) {
        fillPlaceholder(missionDialog, powerPlaceholder1, String.valueOf(percentage).concat("%"));
    }

    public void setPowerTwoPlaceholder(MissionDialog missionDialog, String powerPlaceholder2, int percentage) {
        fillPlaceholder(missionDialog, powerPlaceholder2, String.valueOf(percentage).concat("%"));
    }

    private void fillPlaceholder(MissionDialog missionDialog, String placeholder, String actualValue) {
        if (missionDialog.getGameGoals() != null && missionDialog.getGameGoals().contains(placeholder)) {
            missionDialog.setGameGoals(missionDialog.getGameGoals().replace(placeholder, actualValue));
        }
        if (missionDialog.getCognitiveSkillsGoals() != null && missionDialog.getCognitiveSkillsGoals().contains(placeholder)) {
            missionDialog.setCognitiveSkillsGoals(missionDialog.getCognitiveSkillsGoals().replace(placeholder, actualValue));
        }
        if (missionDialog.getBehavioralChangesChild() != null && missionDialog.getBehavioralChangesChild().contains(placeholder)) {
            missionDialog.setBehavioralChangesChild(missionDialog.getBehavioralChangesChild().replace(placeholder, actualValue));
        }
        if (missionDialog.getBehavioralChangesAdult() != null && missionDialog.getBehavioralChangesAdult().contains(placeholder)) {
            missionDialog.setBehavioralChangesAdult(missionDialog.getBehavioralChangesAdult().replace(placeholder, actualValue));
        }
        if (missionDialog.getFailedRunner() != null && missionDialog.getFailedRunner().contains(placeholder)) {
            missionDialog.setFailedRunner(missionDialog.getFailedRunner().replace(placeholder, actualValue));
        }
        if (missionDialog.getPassedRunner1Star() != null && missionDialog.getPassedRunner1Star().contains(placeholder)) {
            missionDialog.setPassedRunner1Star(missionDialog.getPassedRunner1Star().replace(placeholder, actualValue));
        }
        if (missionDialog.getPassedRunner2Star() != null && missionDialog.getPassedRunner2Star().contains(placeholder)) {
            missionDialog.setPassedRunner2Star(missionDialog.getPassedRunner2Star().replace(placeholder, actualValue));
        }
        if (missionDialog.getPassedRunner3Star() != null && missionDialog.getPassedRunner3Star().contains(placeholder)) {
            missionDialog.setPassedRunner3Star(missionDialog.getPassedRunner3Star().replace(placeholder, actualValue));
        }
        if (missionDialog.getWhatsNextFailedRunner() != null && missionDialog.getWhatsNextFailedRunner().contains(placeholder)) {
            missionDialog.setWhatsNextFailedRunner(missionDialog.getWhatsNextFailedRunner().replace(placeholder, actualValue));
        }
        if (missionDialog.getFailedRunnerNoAttempt() != null && missionDialog.getFailedRunnerNoAttempt().contains(placeholder)) {
            missionDialog.setFailedRunnerNoAttempt(missionDialog.getFailedRunnerNoAttempt().replace(placeholder, actualValue));
        }
        if (missionDialog.getFailedAttempt() != null && missionDialog.getFailedAttempt().contains(placeholder)) {
            missionDialog.setFailedAttempt(missionDialog.getFailedAttempt().replace(placeholder, actualValue));
        }
        if (missionDialog.getPassedAttempt() != null && missionDialog.getPassedAttempt().contains(placeholder)) {
            missionDialog.setPassedAttempt(missionDialog.getPassedAttempt().replace(placeholder, actualValue));
        }
        if (missionDialog.getMissionNumberFocus() != null && missionDialog.getMissionNumberFocus().contains(placeholder)) {
            missionDialog.setMissionNumberFocus(missionDialog.getMissionNumberFocus().replace(placeholder, actualValue));
        }
        if (missionDialog.getFocusDefinitionPopup() != null && missionDialog.getFocusDefinitionPopup().contains(placeholder)) {
            missionDialog.setFocusDefinitionPopup(missionDialog.getFocusDefinitionPopup().replace(placeholder, actualValue));
        }
        if (missionDialog.getImpulsControlDefinition() != null && missionDialog.getImpulsControlDefinition().contains(placeholder)) {
            missionDialog.setImpulsControlDefinition(missionDialog.getImpulsControlDefinition().replace(placeholder, actualValue));
        }
    }
}
