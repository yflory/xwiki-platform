/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package com.xpn.xwiki.wysiwyg.client.plugin.link.exec;

import org.xwiki.gwt.dom.client.Element;
import org.xwiki.gwt.dom.client.Range;

import com.xpn.xwiki.wysiwyg.client.widget.rta.RichTextArea;
import com.xpn.xwiki.wysiwyg.client.widget.rta.cmd.internal.AbstractExecutable;

/**
 * Executable for the unlink command, to remove a link in the wiki document. The command will be enabled whenever a 
 * link is selected, according to the algorithm described by {@link LinkExecutableUtils#getSelectedAnchor(RichTextArea).
 * 
 * @version $Id$
 */
public class UnlinkExecutable extends AbstractExecutable
{
    /**
     * {@inheritDoc}
     * 
     * @see AbstractExecutable#execute(RichTextArea, String)
     */
    public boolean execute(RichTextArea rta, String param)
    {

        // Get the selected anchor
        Element selectedAnchor = LinkExecutableUtils.getSelectedAnchor(rta);
        if (selectedAnchor == null) {
            return false;
        }
        Range range = rta.getDocument().getSelection().getRangeAt(0);
        // unlink
        // but first check where is the selection. If the selection is a caret and is at one side of the anchor, just
        // move the caret out instead of removing the link
        boolean moveSelection = range.isCollapsed();
        boolean isBeginning = false;
        boolean isEnd = false;
        if (moveSelection) {
            // check if it's at the beginning or at the end
            isBeginning = (domUtils.getFirstAncestor(domUtils.getPreviousLeaf(range), 
                LinkExecutableUtils.ANCHOR_TAG_NAME) != selectedAnchor) && range.getStartOffset() == 0;
            isEnd = (domUtils.getFirstAncestor(domUtils.getNextLeaf(range), 
                LinkExecutableUtils.ANCHOR_TAG_NAME) != selectedAnchor) 
                && range.getEndOffset() == domUtils.getLength(range.getEndContainer());
        }
        if (moveSelection && (isEnd || isBeginning)) {
            // cursor it's at the beginning or at the end, move it out of the anchor
            Range newRange = rta.getDocument().createRange();
            if (isBeginning) {
                newRange.setStartBefore(selectedAnchor);
            }
            if (isEnd) {
                newRange.setStartAfter(selectedAnchor);
            }
            newRange.collapse(true);
            // now set it on the document
            rta.getDocument().getSelection().removeAllRanges();
            rta.getDocument().getSelection().addRange(newRange);
        } else {
            selectedAnchor.unwrap();
        }
        return true;
    }

    /**
     * {@inheritDoc}
     * 
     * @see AbstractExecutable#isEnabled(RichTextArea)
     */
    public boolean isEnabled(RichTextArea rta)
    {
        // check that there is a selected anchor
        return LinkExecutableUtils.getSelectedAnchor(rta) != null;
    }
}
