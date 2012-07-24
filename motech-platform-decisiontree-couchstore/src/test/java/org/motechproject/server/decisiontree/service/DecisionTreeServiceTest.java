package org.motechproject.server.decisiontree.service;


import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.motechproject.decisiontree.domain.TreeDao;
import org.motechproject.decisiontree.model.ITreeCommand;
import org.motechproject.decisiontree.model.Node;
import org.motechproject.decisiontree.model.Transition;
import org.motechproject.decisiontree.model.Tree;
import org.motechproject.decisiontree.repository.AllTrees;
import org.motechproject.server.decisiontree.TreeNodeLocator;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;


public class DecisionTreeServiceTest {
    @Mock
    private AllTrees allTrees;

    @Mock
    private TreeNodeLocator treeNodeLocator;

    private DecisionTreeService decisionTreeService;

    private TreeDao pillReminderTree;
    private Node rootNode;
    private Node nextNode;

    @Before
    public void SetUp() {
        initMocks(this);
        nextNode = new Node()
                .setTreeCommands(new NextCommand());
        rootNode = new Node()
                .setTreeCommands(new RootNodeCommand())
                .setTransitions(new Object[][]{
                        {"1", new Transition()
                                .setName("pillTakenOnTime")
                                .setDestinationNode(nextNode)

                        }
                });

        pillReminderTree = new TreeDao( new Tree()
                .setName("PillReminderTree")
                .setRootNode(rootNode));

        when(allTrees.findByName(pillReminderTree.name())).thenReturn(pillReminderTree);
        decisionTreeService = new DecisionTreeServiceImpl(allTrees, treeNodeLocator);
    }

    @Test
    public void shouldFetchCommandForRootNode() {
        when(treeNodeLocator.findNode(pillReminderTree.getTree(), "")).thenReturn(rootNode);
        Node nextNode = decisionTreeService.getNode(pillReminderTree.name(), "");
        assertEquals(RootNodeCommand.class, nextNode.getTreeCommands().get(0).getClass());
    }

    @Test
    public void shouldFetchNextCommand() {
        when(treeNodeLocator.findNode(pillReminderTree.getTree(), "/1")).thenReturn(nextNode);
        Node nextNode = decisionTreeService.getNode(pillReminderTree.name(), "/1");
        assertEquals(NextCommand.class, nextNode.getTreeCommands().get(0).getClass());
    }

    private class RootNodeCommand implements ITreeCommand {
        public String[] execute(Object obj) {
            return null;
        }
    }

    private class NextCommand implements ITreeCommand {
        public String[] execute(Object obj) {
            return null;
        }
    }
}
