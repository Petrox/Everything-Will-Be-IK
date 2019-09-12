/*

Copyright (c) 2015 Eron Gjoni

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and 
associated documentation files (the "Software"), to deal in the Software without restriction, including 
without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell 
copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, 
INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR
PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION 
WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE. 

 */
package IK.floatIK;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.WeakHashMap;

import IK.floatIK.StrandedArmature.Strand;
import sceneGraph.math.floatV.AbstractAxes;
import sceneGraph.math.floatV.Basis;
import sceneGraph.math.floatV.MRotation;
import sceneGraph.math.floatV.MathUtils;
import sceneGraph.math.floatV.Matrix3f;
import sceneGraph.math.floatV.Quaternionf;
import sceneGraph.math.floatV.Rot;
import sceneGraph.math.floatV.SGVec_3f;
import sceneGraph.math.floatV.sgRayf;

/**
 * @author Eron Gjoni
 *
 */
public class SegmentedArmature {
	public AbstractBone segmentRoot; 
	public AbstractBone segmentTip;
	public AbstractAxes debugTipAxes;
	public AbstractAxes debugTargetAxes;
	public ArrayList<SegmentedArmature> childSegments = new ArrayList<SegmentedArmature>();
	public ArrayList<SegmentedArmature> pinnedDescendants = new ArrayList<SegmentedArmature>();

	HashMap<SegmentedArmature, ArrayList<AbstractBone>> strandMap = new HashMap<SegmentedArmature, ArrayList<AbstractBone>>();
	HashMap<AbstractBone, AbstractAxes> simulatedLocalAxes = new HashMap<AbstractBone, AbstractAxes>();
	HashMap<AbstractBone, AbstractAxes> simulatedConstraintAxes = new HashMap<AbstractBone, AbstractAxes>();
	public HashMap<AbstractBone, ArrayList<Rot>> boneRotationMap = new HashMap<AbstractBone, ArrayList<Rot>>();
	WeakHashMap<Rot, SegmentedArmature> rotationStrandMap = new WeakHashMap<Rot, SegmentedArmature>();
	ArrayList<AbstractBone> strandsBoneList = new ArrayList<AbstractBone>();

	private SegmentedArmature parentSegment = null;
	private boolean basePinned = false; 
	private boolean tipPinned = false;
	private boolean processed  = false; 
	public int distanceToRoot = 0;

	public int chainLength = 0;
	boolean includeInIK = true;

	public SegmentedArmature(AbstractBone rootBone) {
		debugTipAxes = rootBone.localAxes.getGlobalCopy();
		debugTipAxes.setParent(rootBone.parentArmature.localAxes());
		debugTargetAxes = rootBone.localAxes.getGlobalCopy();
		debugTargetAxes.setParent(rootBone.parentArmature.localAxes());
		segmentRoot = armatureRootBone(rootBone);
		generateArmatureSegments();
		ensureAxesHeirarchy();
	}

	public SegmentedArmature(SegmentedArmature inputParentSegment, AbstractBone inputSegmentRoot) {
		debugTipAxes = inputSegmentRoot.localAxes.getGlobalCopy();
		debugTipAxes.setParent(inputSegmentRoot.parentArmature.localAxes());
		debugTargetAxes = inputSegmentRoot.localAxes.getGlobalCopy();
		debugTargetAxes.setParent(inputSegmentRoot.parentArmature.localAxes());
		this.segmentRoot = inputSegmentRoot;
		this.setParentSegment(inputParentSegment);
		this.distanceToRoot = this.getParentSegment().distanceToRoot+1;
		generateArmatureSegments();  
	}

	private void generateArmatureSegments() {
		childSegments.clear();
		//pinnedDescendants.clear();
		setTipPinned(false);
		if(segmentRoot.getParent() != null && segmentRoot.getParent().isPinned()) 
			this.setBasePinned(true);
		else 
			this.setBasePinned(false); 

		AbstractBone tempSegmentTip = this.segmentRoot;
		this.chainLength = -1;
		while(true) {
			this.chainLength++;
			ArrayList<AbstractBone> childrenWithPinnedDescendants = tempSegmentTip.returnChildrenWithPinnedDescendants();

			if(childrenWithPinnedDescendants.size() > 1 || (tempSegmentTip.isPinned())) {
				if(tempSegmentTip.isPinned()) setTipPinned(true); 
				//else tipPinned = false;
				this.segmentTip = tempSegmentTip; 

				for(AbstractBone childBone: childrenWithPinnedDescendants) {
					this.childSegments.add(new SegmentedArmature(this, childBone));
				}

				break;
			} else if (childrenWithPinnedDescendants.size() == 1) {
				tempSegmentTip = childrenWithPinnedDescendants.get(0);
			} else {
				this.segmentTip = tempSegmentTip; 
				break;
			}
		}
		updatePinnedDescendants();	    
		generateStrandMaps(); 
	}

	/**
	 * Should only be called from the rootmost strand. 
	 * ensures the proper axes parent relationships
	 *for simulatedAxes throughout the SegmentedArmature .  
	 */
	private void ensureAxesHeirarchy() {
		SegmentedArmature rootStrand = this; 
		while(rootStrand.parentSegment != null) {
			rootStrand = rootStrand.parentSegment;
		}
		recursivelyEnsureAxesHeirarchyFor(rootStrand.segmentRoot, rootStrand.segmentRoot.parentArmature.localAxes());		
	}

	private void recursivelyEnsureAxesHeirarchyFor(AbstractBone b, AbstractAxes parentTo) {
		SegmentedArmature chain = getChainFor(b); 
		if(chain != null) {
			AbstractAxes simLocalAxes =  chain.simulatedLocalAxes.get(b);
			AbstractAxes simConstraintAxes = chain.simulatedConstraintAxes.get(b);
			simLocalAxes.setParent(parentTo);
			simConstraintAxes.setParent(parentTo);
			for(AbstractBone c : b.getChildren()) {
				chain.recursivelyEnsureAxesHeirarchyFor(c, simLocalAxes);
			}
		}
	}

	public void updateSegmentedArmature() {
		if(this.getParentSegment() != null) {
			this.getParentSegment().updateSegmentedArmature();
		} else { 
			generateArmatureSegments();
			ensureAxesHeirarchy();
		}
	}

	public void generateStrandMaps(){
		for(AbstractAxes a : simulatedConstraintAxes.values()) {
			a.emancipate();
		}
		for(AbstractAxes a : simulatedLocalAxes.values()) {
			a.emancipate();
		}

		simulatedLocalAxes.clear();
		simulatedConstraintAxes.clear();
		boneRotationMap.clear();
		strandMap.clear(); 
		strandsBoneList.clear();

		AbstractBone currentBone = segmentTip; 
		AbstractBone stopOn = segmentRoot;
		while(currentBone != null) {
			AbstractAxes ax = simulatedLocalAxes.get(currentBone);
			if(ax == null) { 
				simulatedLocalAxes.put(currentBone, currentBone.localAxes().getGlobalCopy());
				simulatedConstraintAxes.put(currentBone, currentBone.getMajorRotationAxes().getGlobalCopy());
				boneRotationMap.put(currentBone, new ArrayList<Rot>());
			}

			if(currentBone == stopOn) break;
			currentBone = currentBone.getParent();

		}

		strandsBoneList.addAll(boneRotationMap.keySet());
	}

	public ArrayList<AbstractBone> getStrandFromTip(AbstractBone pinnedBone) {
		ArrayList<AbstractBone> result = new ArrayList<AbstractBone>();

		if(pinnedBone.isPinned()) {
			result.add(pinnedBone);
			AbstractBone currBone = pinnedBone.getParent();
			//note to self -- try removing the currbone.parent != null condition
			while(currBone != null && currBone.getParent() != null) {
				result.add(currBone);
				if(currBone.getParent().isPinned()) {
					break;
				}
				currBone = currBone.getParent();
			}			
		}

		return result;
	}

	public void updatePinnedDescendants() {    
		pinnedDescendants.clear();
		pinnedDescendants = this.returnSegmentPinnedNodes();
	}

	public ArrayList<SegmentedArmature> returnSegmentPinnedNodes() {
		ArrayList<SegmentedArmature> innerPinnedChains = new ArrayList<SegmentedArmature>();
		if(this.isTipPinned()) {
			innerPinnedChains.add(this); 
		} else { 
			for(SegmentedArmature childSegment : childSegments) {				
				innerPinnedChains.addAll(childSegment.returnSegmentPinnedNodes());
			}
		}
		return innerPinnedChains;
	}
	
	
	public void updateOptimalRotaitonToPinnedDescendants(
			AbstractBone forBone, 
			int modeCode,
			float dampening) {
		
		int passCount = 1; 
		if((modeCode &1) != 0) passCount++;   
		if((modeCode &2) != 0) passCount++;   
		if((modeCode &4) != 0) passCount++; 
		
		SGVec_3f [] localizedTipHeadings = new SGVec_3f[pinnedDescendants.size()*passCount]; 
		SGVec_3f[] localizedTargetHeadings = new SGVec_3f[pinnedDescendants.size()*passCount]; 
		float[] weights = new float[pinnedDescendants.size()*passCount];
		AbstractAxes thisBoneAxes = simulatedLocalAxes.get(forBone);
		
		for(int i=0; i< pinnedDescendants.size(); i++) {
			SegmentedArmature s = pinnedDescendants.get(i);
			AbstractAxes tipAxes = s.simulatedLocalAxes.get(s.segmentTip);
			AbstractAxes targetAxes = s.segmentTip.getPinnedAxes();
			tipAxes.updateGlobal();
			targetAxes.updateGlobal();
			
			localizedTipHeadings[i*passCount] =  SGVec_3f.sub(tipAxes.origin_(), thisBoneAxes.origin_());
			localizedTargetHeadings[i*passCount] = SGVec_3f.sub(targetAxes.origin_(), thisBoneAxes.origin_());
			weights[i*passCount] = s.segmentTip.getIKPin().getPinWeight();
			
			if((modeCode & 1<<0) != 0) {
				sgRayf xTip = tipAxes.x_norm_();
				sgRayf xTarget = targetAxes.x_norm_();
				localizedTipHeadings[(i*passCount)+1] = SGVec_3f.sub(xTip.p2(), thisBoneAxes.origin_());
				localizedTargetHeadings[(i*passCount)+1] = SGVec_3f.sub(xTarget.p2(), thisBoneAxes.origin_());
				weights[(i*passCount)+1] = s.segmentTip.getIKPin().getPinWeight();;
			}
			if((modeCode & 1<<1) != 0) {
				sgRayf yTip = tipAxes.y_norm_();
				sgRayf yTarget = targetAxes.y_norm_();
				localizedTipHeadings[(i*passCount)+2] = SGVec_3f.sub(yTip.p2(), thisBoneAxes.origin_());
				localizedTargetHeadings[(i*passCount)+2] = SGVec_3f.sub(yTarget.p2(), thisBoneAxes.origin_());
				weights[(i*passCount)+2] = s.segmentTip.getIKPin().getPinWeight();;
			}
			if((modeCode & 1<<2) != 0) {
				sgRayf zTip = tipAxes.z_norm_();
				sgRayf zTarget = targetAxes.z_norm_();
				localizedTipHeadings[(i*passCount)+3] = SGVec_3f.sub(zTip.p2(), thisBoneAxes.origin_());
				localizedTargetHeadings[(i*passCount)+3] = SGVec_3f.sub(zTarget.p2(), thisBoneAxes.origin_());	
				weights[(i*passCount)+3] = s.segmentTip.getIKPin().getPinWeight();;
			}
		}
		
		/*KabschAlignment orientationAligner = new KabschAlignment(localizedTipHeadings, null, localizedTargetHeadings, null);
		orientationAligner.align();
		Rot rotBy = orientationAligner.getRotation();*/	
		QCP qcpOrientationAligner = new QCP(MathUtils.FLOAT_ROUNDING_ERROR, MathUtils.FLOAT_ROUNDING_ERROR);
		Rot qcpRot = qcpOrientationAligner.superpose(localizedTargetHeadings, localizedTipHeadings);
		
		
		Rot dampened = new Rot(qcpRot.getAxis(), Math.min(qcpRot.getAngle(), MathUtils.clamp(qcpRot.getAngle(), -dampening, dampening)));
		thisBoneAxes.rotateBy(dampened);
		thisBoneAxes.markDirty(); thisBoneAxes.updateGlobal();
		forBone.setAxesToSnapped(thisBoneAxes, simulatedConstraintAxes.get(forBone));
		thisBoneAxes.markDirty(); thisBoneAxes.updateGlobal();
	}
	


	/**
	 * 
	 * @param chainMember
	 * @return returns the segment chain (pinned or unpinned, doesn't matter) to which the inputBone belongs. 
	 */
	public SegmentedArmature getChainFor(AbstractBone chainMember) {
		//AbstractBone candidate = this.segmentTip; 
		SegmentedArmature result = null; 
		if(this.parentSegment != null) 
			result = this.parentSegment.getAncestorSegmentContaining(chainMember);
		if(result == null)
			result = getChildSegmentContaining(chainMember);
		return result;
	}	
	public SegmentedArmature getChildSegmentContaining(AbstractBone b) {
		if(strandsBoneList.contains(b)) { 
			return this;
		} else {
			for(SegmentedArmature s : childSegments) {
				SegmentedArmature childContaining = s.getChildSegmentContaining(b);
				if(childContaining != null) 
					return childContaining;
			}
		}
		return null;
	}

	public SegmentedArmature getAncestorSegmentContaining(AbstractBone b) {
		if(strandsBoneList.contains(b)) 
			return this;
		else if(this.parentSegment != null) 
			return this.parentSegment.getAncestorSegmentContaining(b);
		else 
			return null;
	}

	/**
	 * this function travels rootward through the chain hierarchy until it reaches a chain whose base is pinned.
	 * @return returns the first chain encountered with a pinned base. Or, null if it reaches an unpinned armature root.
	 */
	public SegmentedArmature getPinnedRootChainFromHere() {

		SegmentedArmature currentChain = this;
		while(true && currentChain !=null) {
			if(currentChain.isBasePinned()) return currentChain;
			else currentChain = currentChain.getParentSegment();
		}

		return currentChain;

	}

	public AbstractBone armatureRootBone(AbstractBone rootBone2) {
		AbstractBone rootBone = rootBone2;
		while(rootBone.getParent() != null) {
			rootBone = rootBone.getParent();
		} 
		return rootBone;
	}

	public boolean isTipPinned() {
		return tipPinned;
	}

	public void setTipPinned(boolean tipPinned) {
		this.tipPinned = tipPinned;
	}

	public boolean isBasePinned() {
		return basePinned;
	}

	public void setBasePinned(boolean basePinned) {
		this.basePinned = basePinned;
	}

	public SegmentedArmature getParentSegment() {
		return parentSegment;
	}

	public void setParentSegment(SegmentedArmature parentSegment) {
		this.parentSegment = parentSegment;
	}

	/**
	 * aligns all simulation axes from this root of this chain  up until the pinned tips
	 * of any child chains  with the constraint an local axes of their corresponding bone. 
	 */

	public void alignSimulationAxesToBones() {
		for(SegmentedArmature c : pinnedDescendants) {
			recursivelyAlignSimAxesRootwardFrom(c.segmentTip);
		}
	}


	public void recursivelyAlignSimAxesRootwardFrom(AbstractBone b) {
		if(b!= null) {
			SegmentedArmature bChain = getChainFor(b);			
			AbstractBone parent = b.getParent(); 
			AbstractAxes bAxes = bChain.simulatedLocalAxes.get(b); 
			AbstractAxes cAxes = bChain.simulatedConstraintAxes.get(b);
			bChain.simAligned = true;
			bAxes.alignGlobalsTo(b.localAxes());
			bAxes.markDirty(); bAxes.updateGlobal();			
			cAxes.alignGlobalsTo(b.getMajorRotationAxes());
			cAxes.markDirty(); cAxes.updateGlobal();			
			if(parent != null) {
				SegmentedArmature bParentChain = getChainFor(parent);
				if(bParentChain != bChain && bParentChain.simAligned) {
					return; // the parent chain doesn't need aligning, it is safe to just update these simAxes
				}
				recursivelyAlignSimAxesRootwardFrom(parent); 
			}			
			if(bAxes == null) {
				int debug = 0;
			}
			if(Float.isNaN(bAxes.globalMBasis.rotation.getAngle())) {
				int debug = 0;
			}
		}	
	}

	/**aligns this bone and all relevant childBones to their coresponding simulatedAxes (if any) in the SegmentedArmature
	 * @param b bone to start from
	 */
	public void recursivelyAlignBonesToSimAxesFrom(AbstractBone b) {
		SegmentedArmature chain =getChainFor(b);		
		if(chain != null && chain.simAligned) {			
			AbstractAxes simulatedLocalAxes = chain.simulatedLocalAxes.get(b);
			if(b.getParent() == null) {
				b.localAxes().alignGlobalsTo(simulatedLocalAxes);
			} else {
				b.localAxes().localMBasis.rotateTo(simulatedLocalAxes.localMBasis.rotation);
				b.localAxes().markDirty(); b.localAxes().updateGlobal();
			}
			for(AbstractBone bc: b.getChildren()) {
				recursivelyAlignBonesToSimAxesFrom(bc);	
			}			
			chain.simAligned = false;
			chain.processed = false;
		} else {
			int debug = 0;
		}

	}

	/**
	 * popultes the given arraylist with the rootmost unprocessed chains of this segmented armature 
	 * and its descenedants up until their pinned tips. 
	 * @param segments
	 */
	public void getRootMostUnprocessedChains(ArrayList<SegmentedArmature> segments) {
		if(!this.processed) {
			segments.add(this);
		} else {
			if(this.tipPinned) 
				return; 
			for(SegmentedArmature c: childSegments) {
				c.getRootMostUnprocessedChains(segments);
			}
		}
	}



	public void setProcessed(boolean b) {
		this.processed = b;
		if(processed == false) {
			for(SegmentedArmature c : childSegments) {
				c.setProcessed(false);
			}
		}
	}



	private boolean simAligned = false;



}