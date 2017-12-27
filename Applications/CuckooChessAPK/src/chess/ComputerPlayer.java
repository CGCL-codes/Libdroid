/*
    CuckooChess - A java chess program.
    Copyright (C) 2011  Peter Österlund, peterosterlund2@gmail.com

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package chess;


import java.security.SecureRandom;
import java.util.List;
import java.util.Random;

import org.meicorl.unikernel.lib.ControlMessages;
import org.meicorl.unikernel.lib.ExecutionController;
import org.petero.cuckoochess.CuckooChess;

import com.google.gson.Gson;

import android.content.Context;
import android.util.Log;

/**
 * A computer algorithm player.
 * @author petero
 */
public class ComputerPlayer implements Player {
    public static final String engineName;
   
    private static final String TAG = "ComputerPlayer";

    static {
        String name = "CuckooChess 1.13a9";
        try {
            String m = System.getProperty("sun.arch.data.model");
            if ("32".equals(m))
                name += " 32-bit";
            else if ("64".equals(m))
                name += " 64-bit";
        } catch (SecurityException ex) {
            // getProperty not allowed in applets
        }
        engineName = name;
    }

    int minTimeMillis;
    int maxTimeMillis;
    int maxDepth;
    int maxNodes;
    boolean alwaysLocal;
    public boolean verbose;
    TranspositionTable tt;
    Book book;
    boolean bookEnabled;
    boolean randomMode;
    Search currentSearch;

    Context context;
	private ExecutionController executionController;

    public ComputerPlayer(Context mcontext, ExecutionController controller) {
        minTimeMillis = 10000;
        maxTimeMillis = 10000;
        maxDepth = 12;
        maxNodes = -1;
        verbose = true;
        setTTLogSize(15);
        book = new Book(verbose, mcontext);
        bookEnabled = true;
        randomMode = false;

        this.context = mcontext;
		this.executionController = controller;
    }


    public void setTTLogSize(int logSize) {
        tt = new TranspositionTable(logSize);
    }
    
    public void setMaxDepth(int depth){
    	this.maxDepth = depth;
    }
    
    public void setAlwaysLocal(boolean alwaysLocal){
    	this.alwaysLocal = alwaysLocal;
    }
    
    Search.Listener listener;
    public void setListener(Search.Listener listener) {
        this.listener = listener;
    }

    @Override
    public String getCommand(Position pos, boolean drawOffer, List<Position> history) {
        // Create a search object
        long[] posHashList = new long[200 + history.size()];
        int posHashListSize = 0;
        for (Position p : history) {
            posHashList[posHashListSize++] = p.zobristHash();
        }
        tt.nextGeneration();
        byte generation = tt.getGeneration();
        
        History ht = new History();
        
        if(this.alwaysLocal){
        	this.executionController.setUserChoice(ControlMessages.STATIC_LOCAL);
        }else{
        	this.executionController.setUserChoice(ControlMessages.STATIC_REMOTE);
        }
        
/*        if(CuckooChess.steps >=51 && CuckooChess.steps <= 110){
        	this.executionController.setUserChoice(ControlMessages.STATIC_LOCAL);
        }*/
        
        OffSearch sc = new OffSearch(this.executionController, pos, posHashList, posHashListSize, generation, ht);

        // Determine all legal moves
        MoveGen.MoveList moves = new MoveGen().pseudoLegalMoves(pos);

        // Test for "game over"
        if (moves.size == 0) {
            // Switch sides so that the human can decide what to do next.
            return "swap";
        }

        if (bookEnabled) {
            Move bookMove = book.getBookMove(pos);
            if (bookMove != null) {
                System.out.printf("Book moves: %s\n", book.getAllBookMoves(pos));
                return TextIO.moveToString(pos, bookMove, false);
            }
        }
        
        sc.setListener(listener);
        
        // Find best move using iterative deepening
        //currentSearch = sc;
        Move bestM;
        if ((moves.size == 1) && (canClaimDraw(pos, posHashList, posHashListSize, moves.m[0]) == "")) {
            bestM = moves.m[0];
            bestM.score = 0;
        } else if (randomMode) {
            bestM = findSemiRandomMove(sc.getSearch(), moves);
        } else {
            //sc.timeLimit(minTimeMillis, maxTimeMillis);
            
            long starttime = System.nanoTime();
            Gson gson = new Gson();
            String jsonstr = gson.toJson(sc);
            long endtime = System.nanoTime();
            //Log.d("ComputerPlay", "serial cost "+ (endtime-starttime)/1000000 + " ms : " + jsonstr);
            bestM = sc.iterativeDeepening(moves, maxDepth, maxNodes, verbose);
/*            try {
				Thread.currentThread().sleep(2500);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}*/
            //Toast.makeText(this.context, "search "+maxDepth+" depth cost "+ (System.nanoTime()-endtime)/1000000 + " ms : ", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "search with "+maxDepth+" depth costing "+ (System.nanoTime()-endtime)/1000000 + " ms. Current Steps: " + CuckooChess.steps);
            CuckooChess.steps++ ;
        }
        
        if(bestM == null){
        	return null;
        }
        
        //currentSearch = null;
        String strMove = TextIO.moveToString(pos, bestM, false);
        Log.d("ComputerPlay", "bestM to strMove : " + strMove);

        // Claim draw if appropriate
        if (bestM.score <= 0) {
            String drawClaim = canClaimDraw(pos, posHashList, posHashListSize, bestM);
            if (drawClaim != "")
                strMove = drawClaim;
        }
        return strMove;
    }
    
    /** Check if a draw claim is allowed, possibly after playing "move".
     * @param move The move that may have to be made before claiming draw.
     * @return The draw string that claims the draw, or empty string if draw claim not valid.
     */
    private String canClaimDraw(Position pos, long[] posHashList, int posHashListSize, Move move) {
        String drawStr = "";
        if (Search.canClaimDraw50(pos)) {
            drawStr = "draw 50";
        } else if (Search.canClaimDrawRep(pos, posHashList, posHashListSize, posHashListSize)) {
            drawStr = "draw rep";
        } else {
            String strMove = TextIO.moveToString(pos, move, false);
            posHashList[posHashListSize++] = pos.zobristHash();
            UndoInfo ui = new UndoInfo();
            pos.makeMove(move, ui);
            if (Search.canClaimDraw50(pos)) {
                drawStr = "draw 50 " + strMove;
            } else if (Search.canClaimDrawRep(pos, posHashList, posHashListSize, posHashListSize)) {
                drawStr = "draw rep " + strMove;
            }
            pos.unMakeMove(move, ui);
        }
        return drawStr;
    }

    @Override
    public boolean isHumanPlayer() {
        return false;
    }

    @Override
    public void useBook(boolean bookOn) {
        bookEnabled = bookOn;
    }

    @Override
    public void timeLimit(int minTimeLimit, int maxTimeLimit, boolean randomMode) {
        if (randomMode) {
            minTimeLimit = 0;
            maxTimeLimit = 0;
        }
        minTimeMillis = minTimeLimit;
        maxTimeMillis = maxTimeLimit;
        this.randomMode = randomMode;
        if (currentSearch != null) {
            currentSearch.timeLimit(minTimeLimit, maxTimeLimit);
        }
    }

    @Override
    public void clearTT() {
        tt.clear();
    }

    /** Search a position and return the best move and score. Used for test suite processing. */
    public TwoReturnValues<Move, String> searchPosition(Position pos, int maxTimeMillis) {
        // Create a search object
        long[] posHashList = new long[200];
        tt.nextGeneration();
        History ht = new History();
        Search sc = new Search(pos, posHashList, 0, tt, ht);
        
        // Determine all legal moves
        MoveGen.MoveList moves = new MoveGen().pseudoLegalMoves(pos);
        MoveGen.removeIllegal(pos, moves);
        sc.scoreMoveList(moves, 0);

        // Find best move using iterative deepening
        sc.timeLimit(maxTimeMillis, maxTimeMillis);
        Move bestM = sc.iterativeDeepening(moves, -1, -1, false);

        // Extract PV
        String PV = TextIO.moveToString(pos, bestM, false) + " ";
        UndoInfo ui = new UndoInfo();
        pos.makeMove(bestM, ui);
        PV += tt.extractPV(pos);
        pos.unMakeMove(bestM, ui);

//        tt.printStats();

        // Return best move and PV
        return new TwoReturnValues<Move, String>(bestM, PV);
    }

    private Move findSemiRandomMove(Search sc, MoveGen.MoveList moves) {
        sc.timeLimit(minTimeMillis, maxTimeMillis);
        Move bestM = sc.iterativeDeepening(moves, 1, maxNodes, verbose);
        int bestScore = bestM.score;

        Random rndGen = new SecureRandom();
        rndGen.setSeed(System.currentTimeMillis());

        int sum = 0;
        for (int mi = 0; mi < moves.size; mi++) {
            sum += moveProbWeight(moves.m[mi].score, bestScore);
        }
        int rnd = rndGen.nextInt(sum);
        for (int mi = 0; mi < moves.size; mi++) {
            int weight = moveProbWeight(moves.m[mi].score, bestScore);
            if (rnd < weight) {
                return moves.m[mi];
            }
            rnd -= weight;
        }
        assert(false);
        return null;
    }

    private final static int moveProbWeight(int moveScore, int bestScore) {
        double d = (bestScore - moveScore) / 100.0;
        double w = 100*Math.exp(-d*d/2);
        return (int)Math.ceil(w);
    }
}
