/**
 *  Copyright (C) 2002-2015   The FreeCol Team
 *
 *  This file is part of FreeCol.
 *
 *  FreeCol is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  FreeCol is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with FreeCol.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.sf.freecol.client.control;

import java.awt.Color;
import java.util.logging.Logger;

import javax.swing.SwingUtilities;

import net.sf.freecol.FreeCol;
import net.sf.freecol.client.FreeColClient;
import net.sf.freecol.common.model.FreeColGameObject;
import net.sf.freecol.common.model.FreeColObject;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.GameOptions;
import net.sf.freecol.common.model.Nation;
import net.sf.freecol.common.model.NationOptions.NationState;
import net.sf.freecol.common.model.NationType;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Specification;
import net.sf.freecol.common.networking.ChatMessage;
import net.sf.freecol.common.networking.Connection;
import net.sf.freecol.common.option.MapGeneratorOptions;
import net.sf.freecol.common.option.OptionGroup;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;


/**
 * Handles the network messages that arrives before the game starts.
 */
public final class PreGameInputHandler extends InputHandler {

    private static final Logger logger = Logger.getLogger(PreGameInputHandler.class.getName());
	private Node element;

    public PreGameInputHandler(FreeColClient freeColClient) {
        super(freeColClient);
    }

    @Override
    public synchronized Element handle(Connection connection, Element element) {
        if (element == null) {
            return handleNullElement();
        }

        String type = element.getTagName();

        switch (type) {
            case "addPlayer":
                return handleAddPlayer(element);
            case "chat":
                return handleChat(element);
            case "disconnect":
                return handleDisconnect(element);
            case "error":
                return handleError(element);
            case "logout":
                return handleLogout(element);
            case "multiple":
                return handleMultiple(connection, element);
            case "playerReady":
                return handlePlayerReady(element);
            case "removePlayer":
                return handleRemovePlayer(element);
            case "setAvailable":
                return handleSetAvailable(element);
            case "startGame":
                return handleStartGame(element);
            case "updateColor":
                return handleUpdateColor(element);
            case "updateGame":
                return handleUpdateGame(element);
            case "updateGameOptions":
                return handleUpdateGameOptions(element);
            case "updateMapGeneratorOptions":
                return handleUpdateMapGeneratorOptions(element);
            case "updateNation":
                return handleUpdateNation(element);
            case "updateNationType":
                return handleUpdateNationType(element);
            default:
                return handleUnknown(element);
        }
    }

    private Element handleNullElement() {
        // Logic for handling null element
        return null;
    }

    private Element handleAddPlayer(Element element) {
        Game game = getFreeColClient().getGame();
        Element playerElement = (Element) element.getElementsByTagName(Player.getXMLElementTagName()).item(0);
        String id = FreeColObject.readId(playerElement);
        FreeColGameObject fcgo = game.getFreeColGameObject(id);

        if (fcgo == null) {
            game.addPlayer(new Player(game, playerElement));
        } else {
            fcgo.readFromXMLElement(playerElement);
        }

        getGUI().refreshPlayersTable();
        return null;
    }

    private Element handleChat(Element element) {
        Game game = getGame();
        ChatMessage chatMessage = new ChatMessage(game, element);
        getGUI().displayChatMessage(chatMessage.getPlayer(game), chatMessage.getMessage(), chatMessage.isPrivate());
        return null;
    }

    private Element handleDisconnect(Element element) {
        // Logic for handling "disconnect" type
        return null;
    }
    private static final String MESSAGE_ATTRIBUTE = "message";
    private Element handleError(Element element) {
        getGUI().showErrorMessage((element.hasAttribute("messageID"))
                ? element.getAttribute("messageID")
                : null,
                element.getAttribute(MESSAGE_ATTRIBUTE));
        return null;
    }
    private static final String PLAYER_ATTRIBUTE = "player";
    private Element handleLogout(Element element) {
        Game game = getFreeColClient().getGame();
        String playerId = element.getAttribute(PLAYER_ATTRIBUTE );
        String reason = element.getAttribute("reason");

        if (reason != null && !reason.isEmpty()) {
            logger.info("Client logging out: " + reason);
        }

        Player player = game.getFreeColGameObject(playerId, Player.class);
        game.removePlayer(player);
        getGUI().refreshPlayersTable();

        return null;
    }

    private Element handleMultiple(Connection connection, Element element) {
        NodeList nodes = element.getChildNodes();
        Element reply = null;

        for (int i = 0; i < nodes.getLength(); i++) {
            reply = handle(connection, (Element) nodes.item(i));
        }

        return reply;
    }
    private static final String VALUE_ATTRIBUTE = "value";

    private Element handlePlayerReady(Element element) {
        Game game = getFreeColClient().getGame();
        Player player = game.getFreeColGameObject(element.getAttribute(PLAYER_ATTRIBUTE ), Player.class);
        boolean ready = Boolean.parseBoolean(element.getAttribute(VALUE_ATTRIBUTE));
        player.setReady(ready);
        getGUI().refreshPlayersTable();

        return null;
    }

    private Element handleRemovePlayer(Element element) {
        Game game = getFreeColClient().getGame();
        Element playerElement = (Element) element.getElementsByTagName(Player.getXMLElementTagName()).item(0);
        Player player = new Player(game, playerElement);

        getFreeColClient().getGame().removePlayer(player);
        getGUI().refreshPlayersTable();

        return null;
    }

    private Element handleSetAvailable(Element element) {
        Nation nation = getGame().getSpecification().getNation(element.getAttribute("nation"));
        NationState state = Enum.valueOf(NationState.class, element.getAttribute("state"));
        getFreeColClient().getGame().getNationOptions().setNationState(nation, state);
        getGUI().refreshPlayersTable();
        return null;
    }

    private Element handleStartGame(Element element) {
        new Thread(FreeCol.CLIENT_THREAD + "Starting game") {
            @Override
            public void run() {
                while (getFreeColClient().getGame().getMap() == null) {
                    try {
                        Thread.sleep(200);
                    } catch (Exception ex) {
                    }
                }

                SwingUtilities.invokeLater(() -> {
                    getFreeColClient().getPreGameController().startGame();
                });
            }
        }.start();

        return null;
    }

    private Element handleUpdateColor(Element element) {
        Game game = getFreeColClient().getGame();
        Specification spec = game.getSpecification();
        String str = element.getAttribute("nation");
        Nation nation = spec.getNation(str);

        if (nation == null) {
            logger.warning("Invalid nation: " + str);
            return createErrorElement("Invalid nation: " + str);
        }

        Color color;
        try {
            str = element.getAttribute("color");
            int rgb = Integer.parseInt(str);
            color = new Color(rgb);
        } catch (NumberFormatException nfe) {
            logger.warning("Invalid color: " + str);
            return createErrorElement("Invalid color: " + str);
        }

        // Process the color and nation
        nation.setColor(color);
        getFreeColClient().getGUI().refreshPlayersTable();

        // Return a success element indicating success.
        return createSuccessElement("Color updated successfully for " + nation.getName());
    }

    private Element createErrorElement(String message) {
      
		// Create an error response element with an "error" tag and a message attribute.
       
		Element errorElement = element.getOwnerDocument().createElement("error");
        errorElement.setAttribute(MESSAGE_ATTRIBUTE, message);
        return errorElement;
    }

    private Element createSuccessElement(String successMessage) {
       
		// Create a success response element with a "success" tag and a message attribute.
        Element successElement = element.getOwnerDocument().createElement("success");
        successElement.setAttribute(MESSAGE_ATTRIBUTE, successMessage);
        return successElement;
    }


    private Element handleUpdateGame(Element element) {
        NodeList children = element.getChildNodes();

        if (children.getLength() == 1) {
            FreeColClient fcc = getFreeColClient();
            Game game = fcc.getGame();
            game.readFromXMLElement((Element) children.item(0));
            fcc.addSpecificationActions(game.getSpecification());
        } else {
            logger.warning("Child node expected: " + element.getTagName());
        }

        return null;
    }

    private Element handleUpdateGameOptions(Element element) {
        Game game = getFreeColClient().getGame();
        Element mgoElement = (Element) element.getElementsByTagName(GameOptions.getXMLElementTagName()).item(0);
        Specification spec = game.getSpecification();
        OptionGroup gameOptions = spec.getGameOptions();
        gameOptions.readFromXMLElement(mgoElement);
        spec.clean("update game options (server initiated)");
        getGUI().updateGameOptions();
        return null;
    }

    private Element handleUpdateMapGeneratorOptions(Element element) {
        Element mgoElement = (Element) element.getElementsByTagName(MapGeneratorOptions.getXMLElementTagName())
                .item(0);
        getFreeColClient().getGame().getMapGeneratorOptions().readFromXMLElement(mgoElement);
        getGUI().updateMapGeneratorOptions();
        return null;
    }

    private Element handleUpdateNation(Element element) {
        Game game = getFreeColClient().getGame();
        Player player = game.getFreeColGameObject(element.getAttribute(PLAYER_ATTRIBUTE ), Player.class);
        Nation nation = getGame().getSpecification().getNation(element.getAttribute(VALUE_ATTRIBUTE));
        player.setNation(nation);
        getGUI().refreshPlayersTable();
        return null;
    }

    private Element handleUpdateNationType(Element element) {
        Game game = getFreeColClient().getGame();
        Player player = game.getFreeColGameObject(element.getAttribute(PLAYER_ATTRIBUTE ), Player.class);
        NationType nationType = getGame().getSpecification().getNationType(element.getAttribute(VALUE_ATTRIBUTE));
        player.changeNationType(nationType);
        getGUI().refreshPlayersTable();
        return null;
    }

    private Element handleUnknown(Element element) {
        // Logic for handling unknown type
        return null;
    }
}
