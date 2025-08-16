package com.portal.api.services;


import com.portal.api.dto.request.UpdatePlayerProfileRequest;
import com.portal.api.dto.response.Profile;
import com.portal.api.model.Child;

/**
 * Service interface for managing player-related operations.
 */
public interface PlayerService {

    /**
     * Updates the profile of an existing player identified by their username.
     * The update is performed based on the data provided in the request object.
     *
     * TODO: This likely should return something other than a boolean
     *
     * @param username the unique identifier for the player whose profile is to be updated
     * @param updatePlayerProfileRequest the request object containing the profile update details,
     *                                    including fields such as status and curriculum end date
     * @return true if the player profile is successfully updated, false otherwise
     */
    boolean updatePlayerProfile(String username, UpdatePlayerProfileRequest updatePlayerProfileRequest);

    /**
     * Retrieves the profile of a player based on their username.
     *
     * @param username the unique identifier of the player whose profile is to be retrieved
     * @return the profile object containing the player's information, such as personal details
     *         and player-specific status
     * @throws Exception if an error occurs during the retrieval of the player's profile
     */
    Profile getPlayerProfile(String username) throws Exception;

    /**
     * Updates the drop status of a given child object.
     *
     * @param child the child object whose drop status is to be updated
     * @param dropped the new drop status to be assigned to the child
     * @return the updated child object with the modified drop status
     */
    Child setPlayerDropStatus(Child child, boolean dropped);
}
