package com.portal.api.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;

@Data
@Document(collection = "gamestate")
public class GameState {
	
	@Id
	private String username;

	// intro and outro as boolean
    private Boolean intro_runner_comic_1_1;
    private Boolean intro_runner_comic_1_2;
    private Boolean intro_runner_comic_5_1;
    private Boolean intro_runner_comic_5_3;
    private Boolean intro_transference_comic_1_1;
    private Boolean intro_transference_comic_1_2;
    private Boolean intro_transference_comic_1_3;
    private Boolean intro_transference_comic_2_1;
    private Boolean intro_transference_comic_2_2;
    private Boolean intro_transference_comic_2_3;
    private Boolean intro_transference_comic_3_1;
    private Boolean intro_transference_comic_3_2;
    private Boolean intro_transference_comic_3_3;
    private Boolean intro_transference_comic_4_1;
    private Boolean intro_transference_comic_4_2;
    private Boolean intro_transference_comic_4_3;
    private Boolean intro_transference_comic_5_1;
    private Boolean intro_transference_comic_5_2;
    private Boolean intro_transference_comic_5_3;
    private Boolean outro_runner_comic_1_1;
    private Boolean outro_runner_comic_1_2;
    private Boolean outro_transference_comic_1_1;
    private Boolean outro_transference_comic_1_2;
    private Boolean outro_transference_comic_1_3;
    private Boolean outro_transference_comic_2_1;
    private Boolean outro_transference_comic_2_3;
    private Boolean outro_transference_comic_3_3;
    private Boolean outro_transference_comic_4_1;
    private Boolean outro_transference_comic_4_2;
    private Boolean outro_transference_comic_4_3;
    private Boolean outro_transference_comic_5_3;
    private Boolean showSubtitles;
    private Boolean stageFifteenComplete;
    
    // int variables with "Ach"

    private Integer Ach_BotBane;
    private Integer Ach_BotSniperSpree;
    private Integer Ach_BotSwarm;
    private Integer Ach_Brainiac1;
    private Integer Ach_Brainiac2;
    private Integer Ach_Brainiac3;
    private Integer Ach_BurntBackside;
    private Integer Ach_CoolHand;
    private Integer Ach_CrystalAce;
    private Integer Ach_CrystalCatcher;
    private Integer Ach_CrystalCollector;
    private Integer Ach_CrystalMaster;
    private Integer Ach_CrystalScout;
    private Integer Ach_CrystalSpotter;
    private Integer Ach_Defogger;
    private Integer Ach_EagerRanger;
    private Integer Ach_EagleEye;
    private Integer Ach_ExpertSniffer;
    private Integer Ach_FlexMind;
    private Integer Ach_Focused;
    private Integer Ach_FogSmasher;
    private Integer Ach_GStarRecruit;
    private Integer Ach_Healer;
    private Integer Ach_LabRat;
    private Integer Ach_LaserFocus;
    private Integer Ach_LittleLeague;
    private Integer Ach_MadScientist;
    private Integer Ach_OneSmallStep;
    private Integer Ach_RockSolid;
    private Integer Ach_RookieZapper;
    private Integer Ach_SmogbotSlayer;
    private Integer Ach_SpaceDodger;
    private Integer Ach_SpeedRun;
    private Integer Ach_Speedy;
    private Integer Ach_Stage1;
    private Integer Ach_Stage2;
    private Integer Ach_Stage3;
    private Integer Ach_Stage4;
    private Integer Ach_Stage5;
    private Integer Ach_Supersonic;
    private Integer Ach_SwitchBots;
    private Integer Ach_TakeaHit;
    private Integer Ach_TheRightStuff;
    private Integer Ach_TripleJumper;
    private Integer Ach_Unselfish;
    private Integer Ach_Untouchable;
    private Integer Ach_Vigilant;
    private Integer Ach_YogaBrain;
    
    // All other variables as String
    private String Stat_BestPortBotsStreak;
    private String Stat_BestTimeWithoutHit;
    private String CurrentRank;
    private String Current_App_Version;
    private String Report_Intro;
    private String Sessions;
    private String Stat_BotsShot;
    private String Stat_BrainiacLength1;
    private String Stat_BrainiacLength2;
    private String Stat_BrainiacLength3;
    private String Stat_ClustersShot;
    private String Stat_Crystals;
    private String Stat_Decodes;
    private String Stat_FakersPassed;
    private String Stat_FogWallsCleared;
    private String Stat_HighestCrystalsEarned;
    private String Stat_Jumps;
    private String Stat_Male;
    private String Stat_MaxDecodes;
    private String Stat_PortGroupBotMax;
    private String Stat_Sham;
    private String Stat_Slides;
    private String Stat_SwitchesRode;
    private String Stat_TakeAHitCount;
    private String Stat_TransferenceActive;
    private String Stat_TransferenceCompleted;
    private String TxComplete;
    private String Upload_All_Files;
    private String attentionBurstTier;
    private String gender;
    private String lastLevel;
    private String lastSaveDate;
    private String lastSubLevel;
    private String runnerCompleteTime;
    private String runnerFails;
    private String starsPerMission;
    private String voVolume;
    
    // look into these
    private String prologue_comic;
    private String restPeriodStart;
    private String runnerComplete;
    private String shamToggle;
    private String transferFails;
    private String unlocksPerMission;
    

}