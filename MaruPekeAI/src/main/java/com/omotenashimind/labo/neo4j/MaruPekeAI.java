package com.omotenashimind.labo.neo4j;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Scanner;
import java.util.Set;

import org.apache.commons.lang3.math.NumberUtils;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.index.Index;
import org.neo4j.graphdb.index.IndexHits;
import org.neo4j.graphdb.traversal.Evaluators;
import org.neo4j.graphdb.traversal.TraversalDescription;
import org.neo4j.graphdb.traversal.Traverser;
import org.neo4j.kernel.Traversal;

public class MaruPekeAI {

	public static final int TURN_FIRST = 1;
	public static final int TURN_SECOND = 2;
		
	private static final String FILED_STATUS_NONE = "0";
	private static final String FILED_STATUS_FIRST = "1";
	private static final String FILED_STATUS_SECOND = "2";

	private static final int CONTINUE = 0;
	private static final int WIN_FIRST = 1;
	private static final int WIN_SECOND = 2;
	private static final int DRAW = 3;
	
	private static enum RelTypes implements RelationshipType {
        PASSED
	}
	
	private GraphDatabaseService graphDatabase;
	
	public void setGraphDatabase(GraphDatabaseService graphDatabase) {
		this.graphDatabase = graphDatabase;
	}
	
	private int order;	// AI自身が先攻か後攻かを表す

	private int turnNumber;
	
	private String[] currentFields;
	
	private Set<Integer> emptyFieldSet;
	
	private List<Node> currentNodes;
	
	public void startGame(int firstOrSecond) {
		if(TURN_FIRST == firstOrSecond) {	// 相手が先攻の場合、AI自身が後攻
			order = TURN_SECOND;
		} else if(TURN_SECOND == firstOrSecond) {	// 相手が後攻の場合、AI自身が先攻
			order = TURN_FIRST;
		}
		
		initializeFields(); // ゲームフィールドの初期化.
		
		printFields(currentFields);
		
		int status;
		do {
			turnNumber++;
			
			int selectFieldNumber = 0;
			if(1 == (turnNumber % 2)) {
				if(TURN_FIRST == order)  {
					selectFieldNumber = thinkSelectFiled(currentNodes.get(currentNodes.size() - 1));
				} else {
					selectFieldNumber = requestField();
				}
				currentFields[selectFieldNumber] = FILED_STATUS_FIRST;
			} else {
				if(TURN_SECOND == order)  {
					selectFieldNumber = thinkSelectFiled(currentNodes.get(currentNodes.size() - 1));
				} else {
					selectFieldNumber = requestField();
				}
				currentFields[selectFieldNumber] = FILED_STATUS_SECOND;
			}

			emptyFieldSet.remove(selectFieldNumber);
			printFields(currentFields);
			
			currentNodes.add(findOrCreateNode());	// 現在のフィールドの状態を保存する.
			
		} while(CONTINUE == (status = checkGameFinish(currentFields)));
		
		if(WIN_FIRST == status) {
			if(TURN_FIRST == order) {
				System.out.println("You Lose.");
			} else {
				System.out.println("You Win.");
			}
		} else if(WIN_SECOND == status) {
			if(TURN_FIRST == order) {
				System.out.println("You Win.");
			} else {
				System.out.println("You Lose.");
			}
		} else {
			System.out.println("Draw game.");
		}
		
		saveResult(status); // ゲームの結果を記録・学習させる.
	}
	
	private void initializeFields() {
		currentFields = new String[9];
		
		for(int i = 0; i < currentFields.length; i++) {
			currentFields[i] = FILED_STATUS_NONE;
		}
		
		emptyFieldSet = new HashSet<Integer>();
		for(int i = 0; i < 9; i++) {
			emptyFieldSet.add(i);
		}
		
		currentNodes = new ArrayList<Node>();

		// 開始時点のノードを取得.
		currentNodes.add(findOrCreateNode());
		
		System.out.println(currentNodes.get(0));
		
		turnNumber = 0;
	}
	
	private int requestField() {
		int selectFieldNumber;
		System.out.println("0～8の間の数値を入力してください。");
		
		Scanner in = new Scanner(System.in);
		do {
			String inputValue = in.next();
			if(NumberUtils.isNumber(inputValue)) {
				selectFieldNumber = NumberUtils.toInt(inputValue);
				
				if(9 > selectFieldNumber) {
					break;
				} else {
					System.out.println("0～8の間の数値を入力してください。");
				}
			} else {
				System.out.println("0～8の間の数値を入力してください。");
			}
		} while(true);
		
		return selectFieldNumber;
	}

	private Node findOrCreateNode() {
		String currentFieldsString = convertFieldsToString(currentFields);
		
		Index<Node> fieldsStatusIndex = graphDatabase.index().forNodes("fieldsStatus");
		IndexHits<Node> indexHits = fieldsStatusIndex.get("fields", currentFieldsString);
		
		Node fieldStatusNode = null;
		if(indexHits.hasNext()) {
			fieldStatusNode = indexHits.next();
		} else {
			fieldStatusNode = graphDatabase.createNode();
			fieldStatusNode.setProperty("fields", currentFieldsString);
			fieldsStatusIndex.add(fieldStatusNode, "fields", currentFieldsString);
		}
		
		return fieldStatusNode;
	}
	
	private int thinkSelectFiled(Node currentFieldsNode) {		
		TraversalDescription traversalDescription = Traversal.description();
		traversalDescription = traversalDescription.depthFirst()
				.evaluator(Evaluators.atDepth(1))
				.relationships(RelTypes.PASSED, Direction.OUTGOING)
				.sort(new Comparator<Path>() {
					@Override
					public int compare(Path o1, Path o2) {
						int o1WinCount;
						int o2WinCount;
						if(TURN_FIRST == order) {
							o1WinCount = (int)o1.lastRelationship().getProperty("firstWin");
							o2WinCount = (int)o2.lastRelationship().getProperty("firstWin");
						} else {
							o1WinCount = (int)o1.lastRelationship().getProperty("secondWin");
							o2WinCount = (int)o2.lastRelationship().getProperty("secondWin");
						}
						
						if(o1WinCount < o2WinCount) {
							return 1;
						} else if(o1WinCount > o2WinCount) {
							return -1;
						} else {
							return 0;
						}
					}
					
				});
		
		Traverser traverser = traversalDescription.traverse(currentFieldsNode);
		
		Path wonPath = null;
		Path topPath = traverser.iterator().hasNext() ? traverser.iterator().next() : null;
		
		// 勝率がもっとも高い経路を選択する.
		if(null != topPath) {
			int winCount = 0;
			Relationship relationship = topPath.lastRelationship();
			if(TURN_FIRST == order) {
				winCount = (int)relationship.getProperty("firstWin");
			} else {
				winCount = (int)relationship.getProperty("secondWin");
			}

			if(0 < winCount) {
				wonPath = topPath;
			}
		}
		
		for(Path path : traverser) {
			int winCount = 0;
			Relationship relationship = path.lastRelationship();
			if(TURN_FIRST == order) {
				winCount = (int)relationship.getProperty("firstWin");
			} else {
				winCount = (int)relationship.getProperty("secondWin");
			}
			System.out.println("first win:" + relationship.getProperty("firstWin"));
			System.out.println("second win:" + relationship.getProperty("secondWin"));
			System.out.println("path:" + path);
		}
		
		if(null != wonPath) {
			String fieldsString = (String)wonPath.endNode().getProperty("fields");
			return findNextSelectField(fieldsString);
		} else {
			Set<Integer> notSelectedFieldSet = new HashSet<Integer>();
			notSelectedFieldSet.addAll(emptyFieldSet);
			for(Path path : traverser) {
				String nextFieldsString = (String)path.endNode().getProperty("fields");
				notSelectedFieldSet.remove(findNextSelectField(nextFieldsString));
			}
			
			if(0 < notSelectedFieldSet.size()) {	// まだ選択したことのないフィールドがあれば
				return randomSelectField(notSelectedFieldSet);
			} else {
				// すべての経路が選択済みであったにも関わらず、勝ちの経路が見当たらない場合は
				// せめて引き分けに持っていこうと努力する。
				Path maxDrawPath = null;
				for(Path path : traverser) {
					int drawCount = (int)path.lastRelationship().getProperty("drawCount");
					if(0 < drawCount
							&& drawCount > (
									null != maxDrawPath ? (int)maxDrawPath.lastRelationship().getProperty("drawCount") : 0)
					) {
						maxDrawPath = path;
					}
				}
				
				if(null != maxDrawPath) {
					return findNextSelectField((String)maxDrawPath.endNode().getProperty("fields"));
				} else {
					return randomSelectField(emptyFieldSet);
				}
			}
		}
	}
	
	private int findNextSelectField(String nextFieldsString) {
		int selectedNumber = 0;
		for(int i = 0; i < currentFields.length; i++) {
			if(currentFields[i].charAt(0) != nextFieldsString.charAt(i)) {
				selectedNumber = i;
				break;
			}
		}
		
		return selectedNumber;
	}
	
	private int randomSelectField(Set<Integer> selectableFieldSet) {
		Object[] selectableFields = selectableFieldSet.toArray();
		
		Random random = new Random(System.currentTimeMillis());
		int selectFieldInex = random.nextInt(selectableFields.length);

		return (int)selectableFields[selectFieldInex];
	}
		
	private void saveResult(int status) {
		for(int i = 0; i < (currentNodes.size() - 1); i++) {
			TraversalDescription traversalDescription = Traversal.description();
			traversalDescription = traversalDescription.depthFirst()
					.evaluator(Evaluators.atDepth(1))
					.relationships(RelTypes.PASSED)
					.evaluator(Evaluators.pruneWhereEndNodeIs(currentNodes.get(i + 1)));
			Traverser traverser = traversalDescription.traverse(currentNodes.get(i));
			Relationship relationship = traverser.relationships().iterator().hasNext()
					? traverser.relationships().iterator().next() : null;
			
			if(null != relationship) {
				if(WIN_FIRST == status) {
					relationship.setProperty("firstWin", (int)relationship.getProperty("firstWin") + 1);
					int secondWin = (int)relationship.getProperty("secondWin");
					if(0 < secondWin) {
						relationship.setProperty("secondWin", secondWin - 1);
					}
				} else if(WIN_SECOND == status) {
					int firstWin = (int)relationship.getProperty("firstWin");
					if(0 < firstWin) {
						relationship.setProperty("firstWin", firstWin - 1);
					}
					relationship.setProperty("secondWin", (int)relationship.getProperty("secondWin") + 1);
					System.out.println("save state:" + relationship.getProperty("secondWin"));
				} else {
					relationship.setProperty("drawCount", (int)relationship.getProperty("drawCount") + 1);
				}
			} else {
				relationship = currentNodes.get(i).createRelationshipTo(currentNodes.get(i + 1), RelTypes.PASSED);
				if(WIN_FIRST == status) {
					relationship.setProperty("firstWin", 1);
					relationship.setProperty("secondWin", 0);
					relationship.setProperty("drawCount", 0);
				} else if(WIN_SECOND == status) {
					relationship.setProperty("firstWin", 0);
					relationship.setProperty("secondWin", 1);
					relationship.setProperty("drawCount", 0);
				} else {
					relationship.setProperty("firstWin", 0);
					relationship.setProperty("secondWin", 0);
					relationship.setProperty("drawCount", 1);
				}
			}
		}
	}
	
	private String convertFieldsToString(String[] fields) {
		StringBuilder fieldsString = new StringBuilder();
		
		for(String field : fields) {
			fieldsString.append(field);
		}
		
		return fieldsString.toString();
	}
	
	private void printFields(String[] fields) {
		for(int i = 0; i < 3; i++) {
			for(int j = 0; j < 3; j++) {
				if(FILED_STATUS_FIRST.equals(fields[(i * 3) + j])) {
					System.out.print("○");
				} else if(FILED_STATUS_SECOND.equals(fields[(i * 3) + j])) {
					System.out.print("×");
				} else {
					System.out.print("ー");
				}
			}
			System.out.println();
		}
	}

	private int checkGameFinish(String[] fields) {
		if(!FILED_STATUS_NONE.equals(fields[0])
				&& fields[0].equals(fields[1]) && fields[0].equals(fields[2])) {
			return (fields[0].equals(FILED_STATUS_FIRST) ? WIN_FIRST : WIN_SECOND);
		} else if(!FILED_STATUS_NONE.equals(fields[3])
				&& fields[3].equals(fields[4]) && fields[3].equals(fields[5])) {
			return (fields[3].equals(FILED_STATUS_FIRST) ? WIN_FIRST : WIN_SECOND);
		} else if(!FILED_STATUS_NONE.equals(fields[6])
				&& fields[6].equals(fields[7]) && fields[6].equals(fields[8])) {
			return (fields[6].equals(FILED_STATUS_FIRST) ? WIN_FIRST : WIN_SECOND);
		} else if(!FILED_STATUS_NONE.equals(fields[0])
				&& fields[0].equals(fields[3]) && fields[0].equals(fields[6])) {
			return (fields[0].equals(FILED_STATUS_FIRST) ? WIN_FIRST : WIN_SECOND);
		} else if(!FILED_STATUS_NONE.equals(fields[1])
				&& fields[1].equals(fields[4]) && fields[1].equals(fields[7])) {
			return (fields[1].equals(FILED_STATUS_FIRST) ? WIN_FIRST : WIN_SECOND);
		} else if(!FILED_STATUS_NONE.equals(fields[2])
				&& fields[2].equals(fields[5]) && fields[2].equals(fields[8])) {
			return (fields[2].equals(FILED_STATUS_FIRST) ? WIN_FIRST : WIN_SECOND);
		} else if(!FILED_STATUS_NONE.equals(fields[0])
				&& fields[0].equals(fields[4]) && fields[0].equals(fields[8])) {
			return (fields[0].equals(FILED_STATUS_FIRST) ? WIN_FIRST : WIN_SECOND);
		} else if(!FILED_STATUS_NONE.equals(fields[2])
				&& fields[2].equals(fields[4]) && fields[2].equals(fields[6])) {
			return (fields[2].equals(FILED_STATUS_FIRST) ? WIN_FIRST : WIN_SECOND);
		}
		
		if(0 == emptyFieldSet.size()){
			return DRAW;
		} else {
			return CONTINUE;
		}
	}
	
	
}
