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

    private void fillPlaceholder(MissionDialog missionDialog, String namePlaceholder, String firstName) {
        if (missionDialog.getGameGoals() != null && missionDialog.getGameGoals().contains(namePlaceholder)) {
            missionDialog.setGameGoals(missionDialog.getGameGoals().replace(namePlaceholder, firstName));
        }
        if (missionDialog.getCognitiveSkillsGoals() != null && missionDialog.getCognitiveSkillsGoals().contains(namePlaceholder)) {
            missionDialog.setCognitiveSkillsGoals(missionDialog.getCognitiveSkillsGoals().replace(namePlaceholder, firstName));
        }
        if (missionDialog.getBehavioralChangesChild() != null && missionDialog.getBehavioralChangesChild().contains(namePlaceholder)) {
            missionDialog.setBehavioralChangesChild(missionDialog.getBehavioralChangesChild().replace(namePlaceholder, firstName));
        }
        if (missionDialog.getBehavioralChangesAdult() != null && missionDialog.getBehavioralChangesAdult().contains(namePlaceholder)) {
            missionDialog.setBehavioralChangesAdult(missionDialog.getBehavioralChangesAdult().replace(namePlaceholder, firstName));
        }
        if (missionDialog.getFailedRunner() != null && missionDialog.getFailedRunner().contains(namePlaceholder)) {
            missionDialog.setFailedRunner(missionDialog.getFailedRunner().replace(namePlaceholder, firstName));
        }
        if (missionDialog.getPassedRunner1Star() != null && missionDialog.getPassedRunner1Star().contains(namePlaceholder)) {
            missionDialog.setPassedRunner1Star(missionDialog.getPassedRunner1Star().replace(namePlaceholder, firstName));
        }
        if (missionDialog.getPassedRunner2Star() != null && missionDialog.getPassedRunner2Star().contains(namePlaceholder)) {
            missionDialog.setPassedRunner2Star(missionDialog.getPassedRunner2Star().replace(namePlaceholder, firstName));
        }
        if (missionDialog.getPassedRunner3Star() != null && missionDialog.getPassedRunner3Star().contains(namePlaceholder)) {
            missionDialog.setPassedRunner3Star(missionDialog.getPassedRunner3Star().replace(namePlaceholder, firstName));
        }
        if (missionDialog.getWhatsNextFailedRunner() != null && missionDialog.getWhatsNextFailedRunner().contains(namePlaceholder)) {
            missionDialog.setWhatsNextFailedRunner(missionDialog.getWhatsNextFailedRunner().replace(namePlaceholder, firstName));
        }
        if (missionDialog.getFailedRunnerNoAttempt() != null && missionDialog.getFailedRunnerNoAttempt().contains(namePlaceholder)) {
            missionDialog.setFailedRunnerNoAttempt(missionDialog.getFailedRunnerNoAttempt().replace(namePlaceholder, firstName));
        }
        if (missionDialog.getFailedAttempt() != null && missionDialog.getFailedAttempt().contains(namePlaceholder)) {
            missionDialog.setFailedAttempt(missionDialog.getFailedAttempt().replace(namePlaceholder, firstName));
        }
        if (missionDialog.getPassedAttempt() != null && missionDialog.getPassedAttempt().contains(namePlaceholder)) {
            missionDialog.setPassedAttempt(missionDialog.getPassedAttempt().replace(namePlaceholder, firstName));
        }
        if (missionDialog.getMissionNumberFocus() != null && missionDialog.getMissionNumberFocus().contains(namePlaceholder)) {
            missionDialog.setMissionNumberFocus(missionDialog.getMissionNumberFocus().replace(namePlaceholder, firstName));
        }
        if (missionDialog.getFocusDefinitionPopup() != null && missionDialog.getFocusDefinitionPopup().contains(namePlaceholder)) {
            missionDialog.setFocusDefinitionPopup(missionDialog.getFocusDefinitionPopup().replace(namePlaceholder, firstName));
        }
        if (missionDialog.getImpulsControlDefinition() != null && missionDialog.getImpulsControlDefinition().contains(namePlaceholder)) {
            missionDialog.setImpulsControlDefinition(missionDialog.getImpulsControlDefinition().replace(namePlaceholder, firstName));
        }
    }
}
