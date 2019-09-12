package sceneGraph.math.doubleV;



import sceneGraph.math.floatV.Vec3f;

public class Basis {

	/**
	 * FIXME:  magnitudes should always remain positive! 
	 * Use determinants to determine handedness.
	 */

	public static SGVec_3d xBase = new SGVec_3d(1,0,0); 
	public static SGVec_3d yBase = new SGVec_3d(0,1,0); 
	public static SGVec_3d zBase = new SGVec_3d(0,0,1); 

	/**
	 * a vector respresnting the translation of this basis relative to its parent. 
	 */
	public SGVec_3d translate = new SGVec_3d(0,0,0);

	/**
	 * xHeading, yHeading, and zHeading represent the direction of this vector's bases
	 * relative to their parent. The orthogonality of these is dependent on the ancestor's
	 * scaling. If all ancestors have uniform scaling, these are guaranteed to remain orthogonal. 
	 * 
	 * if orthogonality is not assured due to non-uniform parent scaling, and the user 
	 * wants to operate on these bases as if they were orthogonal, it is prudent to work
	 * with respect to the rotation of this basis instead of its headings. 
	 * 
	 * note that orthogonality should generally be assured unless this is a world-space matrix or 
	 * has been derived from a world space matrix in which orthogonality was not assured. 
	 */
	/*private SGVec_3d xHeading  = new SGVec_3d(1,0,0); 
	private SGVec_3d yHeading  = new SGVec_3d(0,1,0); 
	private SGVec_3d zHeading  = new SGVec_3d(0,0,1);*/ 

	//public boolean forceOrthonormal = false;
	public SGVec_3d scale = new SGVec_3d(1,1,1);
	public static final int LEFT = -1;
	public static final int RIGHT = 1;

	public static final int NONE = -1;
	public static final int X = 0;
	public static final int Y = 1;
	public static final int Z = 2;

	public boolean[] flippedAxes = new boolean[3];
	public Matrix4d reflectionMatrix = new Matrix4d();
	public Matrix4d inverseReflectionMatrix = new Matrix4d();

	protected int chirality = RIGHT;

	/**
	 * The rotation of this basis relative to its parents.
	 */
	public Rot rotation = new Rot();

	private SGVec_3d scaledXHeading = new SGVec_3d(); 
	private SGVec_3d scaledYHeading = new SGVec_3d(); 
	private SGVec_3d scaledZHeading = new SGVec_3d(); 


	//private Transform3D shearScaleTransform = new Transform3D();

	//private Transform3D composedTransform = new Transform3D();
	//private Transform3D inverseComposedTransform = new Transform3D();

	private Matrix4d composedMatrix = new Matrix4d(); 
	private Matrix4d inverseComposedMatrix = new Matrix4d();
	private Matrix4d shearScaleMatrix = new Matrix4d(); 


	private boolean inversesDirty = true;
	//Matrix4d tempMat = new Matrix4d();

	/**
	 * A basis is a collection of linearly independent rays + their origin. 
	 * Bases can be affine -- supporting uniform scaling, non-uniform scaling
	 * shearing, rotation, and reflection. 
	 * 
	 * rotation is handled behind the scenes as if the basis were of righthanded 
	 * chirality, even if the basis is of left-handed chirality. 
	 * 
	 * when the basis is instantiated, the initial orientation attempts to line up
	 * with the input y-axis, and x-axis (for historical reasons having mostly to do with
	 * this library's original purpose) regardless of chirality. 
	 * 
	 * if it cannot align with both y and z due to non-orthogonality, the orientaiton
	 * aligns its x to the xy plane, and its y precisely to the yheading.
	 *
	 * @param x the x ray. it's p1 value is assumed to be the base's origin. 
	 * @param y the y ray. it's p1 value is ignored for any purpose other than determining direction.
	 * @param z the z ray. it's p1 value is ignored for any purpose other than determining direction.
	 */
	public Basis(sgRayd x, sgRayd y, sgRayd z) {
		this.shearScaleMatrix.idt();
		this.translate.set((SGVec_3d) x.p1().copy());

		this.scale.x = x.mag();
		this.scale.y = y.mag();
		this.scale.z = z.mag();

		SGVec_3d xDirNew =new SGVec_3d(x.heading());
		SGVec_3d yDirNew =new SGVec_3d(y.heading()); 
		SGVec_3d zDirNew = new SGVec_3d(z.heading());   		

		this.rotation = createPrioritzedRotation(xDirNew, yDirNew, zDirNew);

		//if(shearScaleMatrix.determinant() < 0) {		
		//this.rotation = createIdealRotation(xDirNew, yDirNew, zDirNew);
		//} else {
		//this.rotation = new Rot(xBase, yBase, yDirNew, zDirNew);
		//}

		Rot inverseRot = new Rot(this.rotation.rotation.revert());

		inverseRot.applyTo(xDirNew, xDirNew);
		inverseRot.applyTo(yDirNew, yDirNew);
		inverseRot.applyTo(zDirNew, zDirNew);

		setShearXBaseTo(xDirNew, false);
		setShearYBaseTo(yDirNew, false);
		setShearZBaseTo(zDirNew, true);

	}

	public Basis() {
		this.scale.set(1,1,1);
		this.rotation = new Rot();
		this.shearScaleMatrix.idt();
		this.refreshMatrices();
	}

	private Rot createPrioritzedRotation(SGVec_3d xHeading, SGVec_3d yHeading, SGVec_3d zHeading) {
		SGVec_3d tempV = new SGVec_3d();
		Rot toYX = new Rot(yBase, xBase, yHeading, xHeading); 
		toYX.applyTo(yBase, tempV);
		Rot toY = new Rot(tempV, yHeading);

		return toY.applyTo(toYX);
		/*Rot result = new Rot(xBase, yBase, xHeading, yHeading);
		double smallestAngle = result.getAngle(); 
		Rot tempRotation = new Rot(yBase, zBase, yHeading, zHeading);
		double tempAngle = tempRotation.getAngle(); 

		if(tempAngle < smallestAngle) {
			smallestAngle = tempAngle; 
			result = tempRotation;
		}

		tempRotation = new Rot(zBase, xBase, zHeading, xHeading);
		tempAngle = tempRotation.getAngle();

		if(tempAngle < smallestAngle) {
			smallestAngle = tempAngle; 
			result = tempRotation;
		}

		return result;*/
	}


	/**
	 * see: http://matthias-mueller-fischer.ch/publications/stablePolarDecomp.pdf
	 * @param referenceOrientation can be null if not known, in which case, 
	 * the Identity Rotation will be used, but providing something here 
	 * is strongly encouraged as it speeds up convergence. If you call this function 
	 * to recompute the rotation frequently, then usually you should feed its previous output 
	 * rotation to it as the reerence orientation. 
	 * @param maximumIterations maximum number of times to run the optimization loop.
	 * according to the paper, in the overwhelming majority of cases you should get usable results 
	 * in under 5 iterations. According to other papers, you might want to go as high as 12 iterations. 
	 * @param saetyCheck according to the referenced paper, there are theoretical 
	 * cases where this scheme does not converge. The paper maintains that these cases are 
	 * exceedingly rare as per their empirical observations. However, their methodology 
	 * depends on a random sampling of all possible transformations, while their math implies 
	 * that the degenerate cases are most likely to arise in precisely the sorts of situations 
	 * this Basis class is hoping to simplify away -- specifically, reflections across multiple planes.
	 * For this reason, a safety check parameter is made available. If set to true, this 
	 * will (as suggested by the paper) attempt to check if the solver is failing to converge, 
	 * and add a small random perturbation to the input Matrix before running the solver again.
	 *   
	 */
	public Rot extractIdealRotation(Matrix4d affineMatrix, Rot referenceOrientation, int maxIter, boolean safetyCheck) {
		Matrix3d affine3d = new Matrix3d(affineMatrix.val);
		return new Rot(extractIdealRotation(affine3d, referenceOrientation.rotation, maxIter, safetyCheck));
	}
	
	public MRotation extractIdealRotation(Matrix3d affineMatrix, MRotation referenceOrientation, int maxIter, boolean safetyCheck) {
		MRotation q = referenceOrientation.copy();
		Matrix3d A = affineMatrix;
		for (int iter = 0; iter < maxIter; iter++) {
			Matrix3d R = q.getMatrix();
			SGVec_3d col0 = R.col(0).crs(A.col(0));
			SGVec_3d col1 = R.col(1).crs(A.col(1));
			SGVec_3d col2 =R.col(2).crs(A.col(2)); 
			SGVec_3d sum = col0.addCopy(col1).add(col2);
			double mag = 	Math.abs(
					R.col(0).dot(A.col(0)) 
				+ R.col(1).dot(A.col(1)) 
				+ R.col(2).dot(A.col(2)));
			SGVec_3d omega = sum.multCopy(1d/(mag+1.0e-9));					
			double w = omega.mag();
			if (w < 1.0e-9)
				break;
			
			q = new MRotation(omega.div(w), w).multiply(q);					
			q= q.normalize();
		}
		return q;
	}

	public Basis(Basis input) {
		this.adoptValues(input);
	}

	/**
	 * takes on the same values (not references) as the input basis. 
	 * @param in
	 */
	public void adoptValues(Basis in) {
		this.translate = in.translate.copy();
		this.rotation.set(in.rotation);
		this.shearScaleMatrix.set(in.getShearScaleMatrix());
		this.composedMatrix.set(in.getComposedMatrix());

		//this.shearScaleTransform.set(this.shearScaleMatrix);
		//this.composedTransform.set(this.composedMatrix);
		this.reflectionMatrix.set(in.reflectionMatrix);

		this.flippedAxes[X] = in.flippedAxes[X];
		this.flippedAxes[Y] = in.flippedAxes[Y];
		this.flippedAxes[Z] = in.flippedAxes[Z];

		this.inversesDirty = true;
		refreshMatrices();

	}

	public void orthoNormalize() {
		if(flippedAxes[X]) shearScaleMatrix.setColumn(X, -xBase.x, -xBase.y, -xBase.z, 0);
		else shearScaleMatrix.setColumn(X, xBase.x, xBase.y, xBase.z, 0);
		if(flippedAxes[Y]) shearScaleMatrix.setColumn(Y, -yBase.x, -yBase.y, -yBase.z, 0);
		else shearScaleMatrix.setColumn(Y, yBase.x, yBase.y, yBase.z, 0);
		if(flippedAxes[Z]) shearScaleMatrix.setColumn(Z, -zBase.x, -zBase.y, -zBase.z, 0);
		else shearScaleMatrix.setColumn(Z, zBase.x, zBase.y, zBase.z, 0);

		refreshMatrices();
	}

	public Basis copy() {		

		return new Basis(this);
	}
	Matrix4d tempMatrix = new Matrix4d();

	/**
	 * sets the values of local_output such that
	 * 
	 * this.getGlobalOf(local_output) == global_input.  
	 *  
	 * @param input
	 */

	public void setToLocalOf(Basis global_input, Basis local_output) {

		///if a matrix is inverted, reflection should be computed by Reflection *Matrix. 
		//if a matrix is NOT inverted, reflection should be computed by Matrix * Reflection.	


		this.rotation.applyInverseTo(global_input.rotation, local_output.rotation); 
		local_output.composedMatrix.setToMulOf(this.getInverseComposedMatrix(), global_input.composedMatrix);
		this.setToChiralityModifiedRotationOf(local_output.rotation, local_output.rotation);
		//local_output.rotation.set(currentRot);
		//Rot postModRot = this.rotation.applyTo(currentRot);

		local_output.composedMatrix.getColumn(X, arrVec1);		
		local_output.rotation.rotation.applyInverseTo(arrVec1, arrVec2);
		SGVec_3d arrV = new SGVec_3d(); arrV.set(arrVec2);
		//arrV.add(this.translate);
		//SGVec_3d orthonormalVer = new SGVec_3d();  this.setToOrthoNormalLocalOf(arrV, orthonormalVer);
		local_output.shearScaleMatrix.setColumn(X, arrVec2);	

		local_output.composedMatrix.getColumn(Y, arrVec1);		
		local_output.rotation.rotation.applyInverseTo(arrVec1, arrVec2);
		local_output.shearScaleMatrix.setColumn(Y, arrVec2);

		local_output.composedMatrix.getColumn(Z, arrVec1);		
		local_output.rotation.rotation.applyInverseTo(arrVec1, arrVec2);
		local_output.shearScaleMatrix.setColumn(Z, arrVec2);
		//tempMatrix.mul(this.reflectionMatrix, global_input.reflectionMatrix);
		//local_output.shearScaleMatrix.mul(local_output.shearScaleMatrix, tempMatrix);		
		local_output.translate = this.getLocalOf(global_input.translate);

		local_output.refreshMatrices();
	}

	/**
	 * like set to OrientationalLocalOf, but acknowledge chirality.
	 * @param globalInput
	 * @param local_output
	 */
	public void setToOrthoNormalLocalOf(Basis global_input, Basis local_output) {
		this.rotation.applyInverseTo(global_input.rotation, local_output.rotation);//global_input.rotation.applyToInverseOf(this.rotation);
		local_output.shearScaleMatrix.set(global_input.shearScaleMatrix);
		local_output.applyInverseRotTo(this.rotation, global_input.shearScaleMatrix, local_output.shearScaleMatrix);
		local_output.translate = this.getLocalOf(global_input.translate);
		local_output.shearScaleMatrix.setToMulOf(local_output.shearScaleMatrix, this.reflectionMatrix);
		local_output.refreshMatrices();		
	}

	public boolean debug = false;

	/**
	 * sets globalOutput such that the result of 
	 * this.getLocalOf(globalOutput) == localInput. 
	 * 
	 * @param localInput
	 * @param globalOutput
	 */
	public void setToGlobalOf(Basis localInput, Basis globalOutput) {		


		/**
		 * 1. set globalOutput.composedMatrix = to local_input.composedMatrix.  
		 * 2. apply this.shearScaleMatrix to globalOutput.composedMatrix. 
		 * 3. unrotate globalOutput.composedMatrix by this.rotate.
		 * 4. set globalOutput.shearScaleMatrix to globalOutput.composedMatrix. 
		 * 5. rotate globalOutput.composedMatrix by (this.rotation.applyTo(localInput.rotation)) 
		 * 
		 */		


		/*if(debug) {
			boolean[] prevFlip = new boolean[3];
			prevFlip[X] = globalOutput.flippedAxes[X];
			prevFlip[Y] = globalOutput.flippedAxes[Y];
			prevFlip[Z] = globalOutput.flippedAxes[Z];
			System.out.println("debugging");
			System.out.print("pre:  ");
			System.out.println(globalOutput.flippedAxes[X] + ", " + globalOutput.flippedAxes[Y] + ", " + globalOutput.flippedAxes[Z] + "   " + System.identityHashCode(this));
		}*/



		/**
		 * original version, working. but shear scale isn't quite right
		 */
		/*globalOutput.translate = this.getGlobalOf(localInput.translate);
		globalOutput.shearScaleMatrix.mul(this.shearScaleMatrix, localInput.shearScaleMatrix);
		globalOutput.rotation = this.rotation.applyTo(localInput.rotation);
		globalOutput.refreshMatrices();*/


		globalOutput.translate = this.getGlobalOf(localInput.translate);
		tempMatrix.setToMulOf(this.shearScaleMatrix, localInput.composedMatrix);
		//tempMatrix.mul(localInput.composedMatrix, this.reflectionMatrix);
		//applyRotTo(localInput.rotation, tempMatrix, tempMatrix);
		//tempMatrix.mul(tempMatrix, this.reflectionMatrix);
		//tempMatrix.mul(this.shearScaleMatrix, tempMatrix);
		//tempMatrix.mul(this.reflectionMatrix, tempMatrix);
		//applyRotTo(localInput.inverseRotation, tempMatrix, globalOutput.shearScaleMatrix);

		/*localInput.rotation.getAxis(workingVector);
		this.setTupleFromDVec(workingVector, workingPoint);
		this.shearScaleTransform.transform(workingPoint);
		this.setDVecFromTuple(workingVector, workingPoint);
		double angle = localInput.rotation.getAngle();
		if(this.chirality == LEFT) angle *=-1; */
		//globalOutput.rotation = getChrialityModifiedRotationOf(localInput.rotation);//new Rot(workingVector, angle);
		setToChiralityModifiedRotationOf(localInput.rotation, globalOutput.rotation);
		applyInverseRotTo(globalOutput.rotation, tempMatrix, globalOutput.shearScaleMatrix);
		this.rotation.applyTo(globalOutput.rotation, globalOutput.rotation);
		globalOutput.refreshMatrices();
		/*if(debug) {
			System.out.print("post: " );
			System.out.println(globalOutput.flippedAxes[X] + ", " + globalOutput.flippedAxes[Y] + ", " + globalOutput.flippedAxes[Z] + "   " + System.identityHashCode(this));
			System.out.println("" );
			if(prevFlip[X] != globalOutput.flippedAxes[X] ||
			   prevFlip[Y] != globalOutput.flippedAxes[Y] ||
		       prevFlip[Z] != globalOutput.flippedAxes[Z]) {
				System.out.println("FLIPPED!");
			}
		}*/

	}


	/**
	 * This function expects the source base vectors and the target base vectors 
	 * to be of equivalent chirality.
	 * 
	 * @param sourceMatrix a matrix containing the source base vectors to map from
	 * @param targetMatrix a matrix containing the source base vectors to map to
	 * @param flippedAxes an array of 3 elements corresponding to whichever of the 
	 * base vectors have been flipped since instantiation, use Basis.X, Basis.Y, and Basis.Z 
	 * for the corresponding x y z indices. A value of "true" means the base vector has been flipped
	 * an odd number of times, a value of "false" means it has been flipped an even number of times
	 * (in other words, false means the basis has effectively not been flipped)
	 * 
	 * @return this returns a new Rotation object for this basis instance which
	 * maps at least two of the source base vectors onto their corresponding target vectors.
	 * 
	 * If the target bases comprise a right-handed chirality, the rotation 
	 * returned will match all base vectors (presuming orthonormality)
	 * 
	 * If the target bases comprise a left-handed chirality, 
	 * the rotation will align itself in an "odd-one-out" 
	 * scheme. 
	 *  
	 * In other words, (for the sake of explanatory simplification, we  will presume the 
	 * bases are orthogonal, but note that behind the scenes this accounts for shearing) 
	 * result orientation aligns itself such that
	 * if X, Y, Z are the target bases, and x, y, and z are the bases of the orientation this returns,
	 * and ~ means that a target base vector is flipped, and +/- denote that rotation bases head toward or
	 * away from their corresponding target bases respectively, then the following
	 * base vector flip indications will imply (->) the following rotation axes directions:
	 * 
	 * ~X,~Y,~Z -> +x,+y,+z
	 *  
	 *  X,~Y,~Z -> -x,+y,+z
	 * ~X, Y,~Z -> +x,-y,+z 
	 * ~X,~Y, Z -> +x,+y,-z
	 *  
	 * ~X, Y, Z -> -x,+y,+z
	 *  X,~Y, Z -> +x,-y,+z
	 *  X, Y,~Z -> +x,+y,-z
	 *  
	 *  X, Y, Z -> +x,+y,+z
	 * 
	 * If you find yourself in a situation other than accounting for chirality discrepancy where you do not expect
	 * there to be a rotation that can almost perfectly satisfy a mapping from the source bases 
	 * to the target bases, you are likely using this library in ways you might be better off
	 * avoiding. Alternatively, this library might be badly designed. I don't know, it's the first time
	 * I've done this. 
	 */
	public static Rot getRectifiedRotation(Matrix4d sourceMatrix, Matrix4d targetMatrix,
			boolean[] flippedAxes) {
		double [] arrVec1 = new double[4];
		Rot result; 

		int oddBasisOut = getOddBasisOut(flippedAxes);	

		SGVec_3d sourceA = new SGVec_3d(); 
		SGVec_3d sourceB = new SGVec_3d();
		SGVec_3d targetA = new SGVec_3d(); 
		SGVec_3d targetB = new SGVec_3d();

		if(oddBasisOut == X) {
			sourceA = yBase;
			//sourceMatrix.getColumn(Y, arrVec1); sourceA.set(arrVec1);
			targetMatrix.getColumn(Y, arrVec1); targetA.set(arrVec1);

			sourceB = zBase;
			//sourceMatrix.getColumn(Z, arrVec1); sourceB.set(arrVec1);			
			targetMatrix.getColumn(Z, arrVec1); targetB.set(arrVec1);
		} 
		else if(oddBasisOut == Y) {
			sourceA = xBase;
			//sourceMatrix.getColumn(X, arrVec1); sourceA.set(arrVec1);
			targetMatrix.getColumn(X, arrVec1); targetA.set(arrVec1);

			sourceB = zBase;
			//sourceMatrix.getColumn(Z, arrVec1); sourceB.set(arrVec1);			
			targetMatrix.getColumn(Z, arrVec1); targetB.set(arrVec1);
		} 
		else if(oddBasisOut == Z) {
			sourceA = xBase;
			//sourceMatrix.getColumn(X, arrVec1); sourceA.set(arrVec1);
			targetMatrix.getColumn(X, arrVec1); targetA.set(arrVec1);

			sourceB = yBase;
			//sourceMatrix.getColumn(Y, arrVec1); sourceB.set(arrVec1);				
			targetMatrix.getColumn(Y, arrVec1); targetB.set(arrVec1);
		} /*else {
			sourceA = xBase;
			//sourceMatrix.getColumn(X, arrVec1); sourceA.set(arrVec1);
			targetMatrix.getColumn(X, arrVec1); targetA.set(arrVec1);

			sourceB = yBase;
			//sourceMatrix.getColumn(Y, arrVec1); sourceB.set(arrVec1);				
			targetMatrix.getColumn(Y, arrVec1); targetB.set(arrVec1);
		}*/

		result = new Rot(sourceA, sourceB, targetA, targetB);
		return result;		
	}

	public static int getOddBasisOut(boolean[] flipAxes) {
		int oddBasisOut = 0;	
		if(flipAxes[X] == flipAxes[Y])
			oddBasisOut = Z; 
		else if(flipAxes[X] == flipAxes[Z]) 
			oddBasisOut = Y; 
		else
			oddBasisOut = X;	

		return oddBasisOut;
	}

	public Rot getLocalOfRotation(Rot inRot) {
		SGVec_3d tempV = new SGVec_3d();
		inRot.getAxis(tempV);
		//this.getInverseComposedTransform().transform(tempVec);
		this.getInverseComposedOrthoNormalMatrix().transform(tempV, tempV);		
		//double angle = inRot.getAngle(); 
		//angle = inRot.getAngle()*this.chirality;
		return new Rot(tempV, inRot.getAngle()*this.chirality);
	}

	public Matrix4d composedOrthoNormalMatrix = new Matrix4d();
	private Matrix4d inverseComposedOrthoNormalMatrix = new Matrix4d();
	//private Transform3D composedOrthonormalTransform = new Transform3D(); 
	//private Transform3D inverseComposedOrthonormalTransform = new Transform3D(); 

	private Rot getChrialityModifiedRotationOf(Rot localRot) {
		SGVec_3d tempV = new SGVec_3d();
		localRot.getAxis(tempV);
		//this.shearScaleTransform.transform(workingPoint);
		this.reflectionMatrix.transform(tempV, tempV);
		double angle = localRot.getAngle();
		if(this.chirality == LEFT) angle *=-1;
		return new Rot(tempV, angle);
	}

	private void setToChiralityModifiedRotationOf(Rot localRot, Rot outputRot) {
		SGVec_3d tempV = new SGVec_3d();
		localRot.getAxis(tempV);
		//localRot.this.setTupleFromDVec(workingVector, workingPoint);
		//this.shearScaleTransform.transform(workingPoint);
		this.reflectionMatrix.transform(tempV, tempV);
		double angle = localRot.getAngle();
		if(this.chirality == LEFT) angle *=-1;
		outputRot.set(tempV, angle);
	}


	//SGVec_3d tempV_3 = new SGVec_3d(0,0,0);

	/**
	 * @return the x-heading of the orthonormal rotation matrix of this basis.
	 * guaranteed to be orthonormal and right-handed. 
	 */
	public SGVec_3d getRotationalXHead() {
		return this.rotation.applyToCopy(xBase);
	}

	/**
	 * @return the y-heading of the orthonormal rotation matrix of this basis.
	 * guaranteed to be orthonormal and right-handed.
	 */
	public SGVec_3d getRotationalYHead() {
		return this.rotation.applyToCopy(yBase);
	}

	/**
	 * @return the z-heading of the orthonormal rotation matrix of this basis.
	 * guaranteed to be orthonormal and right-handed.
	 */
	public SGVec_3d getRotationalZHead() {
		return this.rotation.applyToCopy(zBase);
	}


	/**
	 * @return the x-heading of the orthonormal rotation matrix of this basis.
	 * guaranteed to be orthonormal but not necessarily right-handed.
	 */
	public SGVec_3d getOrthonormalXHead() {
		//setToShearXBase(workingVector);
		if(!flippedAxes[X])  
			return this.rotation.applyToCopy(xBase);
		else 
			return this.rotation.applyToCopy(SGVec_3d.mult(xBase, -1d));
	}

	/**
	 * @return the y-heading of the orthonormal rotation matrix of this basis.
	 * guaranteed to be orthonormal but not necessarily right-handed.
	 */
	public SGVec_3d getOrthonormalYHead() {
		if(!flippedAxes[Y])  
			return this.rotation.applyToCopy(yBase);
		else 
			return this.rotation.applyToCopy(SGVec_3d.mult(yBase, -1d));
	}

	/**
	 * @return the z-heading of the orthonormal rotation matrix of this basis.
	 * guaranteed to be orthonormal but not necessarily right-handed.
	 */
	public SGVec_3d getOrthonormalZHead() {

		if(!flippedAxes[Z])  
			return this.rotation.applyToCopy(zBase);
		else 
			return this.rotation.applyToCopy(SGVec_3d.mult(zBase, -1d));
	}



	public void rotateTo(Rot newRotation) {				
		this.rotation.set(newRotation); 
		this.refreshMatrices();
	}

	public void rotateBy(Rot addRotation) {		
		addRotation.applyTo(this.rotation, this.rotation);

		//System.out.println("rotateBy: axis = " + addRotation.getAxis().toPVec() + ", andlge = " + addRotation.getAngle());
		this.refreshMatrices();
	}

	//FIXME: Or, at least make sure I work. 

	/**
	 * like set setToOrientationalGlobalOf, but acknowledges chirality. 
	 * @param input
	 * @param output
	 */
	public void setToOrthoNormalGlobalOf(SGVec_3d input, SGVec_3d output) {	
		if(input != null) {
			SGVec_3d tempV = new SGVec_3d(input);
			reflectionMatrix.transform(tempV, tempV);
			this.rotation.applyTo(tempV, tempV);
			output.setX_(tempV.x+translate.x); 
			output.setY_(tempV.y+translate.y); 
			output.setZ_(tempV.z+translate.z);  		

		}
	}

	public void setToOrientationalGlobalOf(SGVec_3d input, SGVec_3d output) {	
		SGVec_3d tempV = new SGVec_3d();
		this.rotation.applyTo(input, tempV);
		output.setX_(tempV.x+translate.x); 
		output.setY_(tempV.y+translate.y); 
		output.setZ_(tempV.z+translate.z);  		
	}

	public void setToGlobalOf(SGVec_3d input, SGVec_3d output) {
		SGVec_3d tempV = new SGVec_3d(input);
		this.composedMatrix.transform(tempV, tempV);		
		output.set(tempV);
		output.add(this.translate); 	
	}


	public void setToGlobalOf(SGVec_3d input) {
		this.setToGlobalOf(input, input);
	}

	public SGVec_3d getGlobalOf(SGVec_3d input) {
		SGVec_3d result = new SGVec_3d(0,0,0); 
		this.setToGlobalOf(input, result);
		return result;
	}

	public void setToLocalOf(SGVec_3d input, SGVec_3d output) {
		SGVec_3d tempV = new SGVec_3d(input);
		tempV.set(input);
		tempV.x -= translate.x; tempV.y -= translate.y; tempV.z -= translate.z; 		

		this.getInverseComposedMatrix().transform(tempV, tempV);
		output.setX_(tempV.x); 
		output.setY_(tempV.y); 
		output.setZ_(tempV.z);
	}

	public SGVec_3d getLocalOf(SGVec_3d global_input) {
		SGVec_3d result = global_input.copy();
		setToLocalOf(global_input, result);
		return result;
	}

	public void setToOrientationalLocalOf(Basis global_input, Basis local_output) {
		this.rotation.applyInverseTo(global_input.rotation, local_output.rotation);//global_input.rotation.applyToInverseOf(this.rotation);
		local_output.shearScaleMatrix.set(global_input.shearScaleMatrix);
		local_output.applyInverseRotTo(this.rotation, local_output.shearScaleMatrix, local_output.composedMatrix);
		local_output.translate = this.getLocalOf(global_input.translate);
		local_output.refreshMatrices();		
	}

	/**
	 * sets output to the value of input in terms of the right-handed
	 * orthonormal basis representing this affine's rotation. 
	 * @param input
	 * @param output
	 */
	public void setToOrientationalLocalOf(SGVec_3d input, SGVec_3d output) {
		SGVec_3d tempV = new SGVec_3d(input);
		tempV.set(input).sub(this.translate);
		this.rotation.applyInverseTo(tempV, tempV);
		output.set(tempV);
	}

	/*public void setToOrthoNormalLocalOf(SGVec_3d input, SGVec_3d output) {		
		this.setToOrientationalLocalOf(input, output);		
		setTupleFromDVec(output, workingPoint);
		tempMatrix.invert(this.reflectionMatrix);
		tempMatrix.transform(this.workingPoint);
		this.setDVecFromTuple(output, workingPoint);		
	}*/

	public void setToOrthoNormalLocalOf(SGVec_3d input, SGVec_3d output) {		
		SGVec_3d tempV = new SGVec_3d(input);
		tempV.set(input); 
		this.setToOrientationalLocalOf(tempV, tempV);		
		this.getInverseReflectionMatrix().transform(tempV, tempV);
		output.set(tempV);
	}


	/*public void orthoNormalize() {
		this.scaleRotationMatrix.setAutoNormalize(true);
		this.scaleRotationMatrix.normalize();
		this.inverseScaleRotation.normalize();		
	}*/

	public void translateTo(SGVec_3d newOrigin) {
		this.translate.x = newOrigin.x;
		this.translate.y = newOrigin.y;
		this.translate.z = newOrigin.z; 
	}


	public void translateBy(SGVec_3d transBy) {
		this.translate.x += transBy.x; 
		this.translate.y += transBy.y;
		this.translate.z += transBy.z;
	}

	public String toString() {
		SGVec_3d tempV = new SGVec_3d();
		setToComposedXBase(tempV);
		Vec3f xh = tempV.toSGVec3f();

		setToComposedYBase(tempV);
		Vec3f yh = tempV.toSGVec3f();

		setToComposedZBase(tempV);
		Vec3f zh = tempV.toSGVec3f();

		float xMag = xh.mag();	
		float yMag = yh.mag();
		float zMag = zh.mag();
		//this.chirality = this.composedMatrix. ? RIGHT : LEFT;
		String chirality = this.chirality == LEFT ? "LEFT" : "RIGHT";
		String result = "-----------\n"  
				+chirality + " handed \n"
				+"origin: " + this.translate + "\n"
				+"rot Axis: " + this.rotation.getAxis().toSGVec3f() + ", "
				+"Angle: " + (float)Math.toDegrees(this.rotation.getAngle()) + "\n"
				+"xHead: " + xh + ", mag: " + xMag + "\n"
				+"yHead: " + yh + ", mag: " + yMag + "\n"
				+"zHead: " + zh + ", mag: " + zMag + "\n";

		return result;
	}	

	public void scaleXTo(double scale) {
		SGVec_3d shearX = new SGVec_3d(); 
		this.setToShearXBase(shearX); 
		shearX.normalize();
		this.setShearXBaseTo(shearX, true);
		scaleXBy(scale);
	}

	public void scaleYTo(double scale) {
		SGVec_3d shearY = new SGVec_3d(); 
		this.setToShearYBase(shearY); 
		shearY.normalize();
		this.setShearYBaseTo(shearY, true);
		scaleYBy(scale);
	}

	public void scaleZTo(double scale) {
		SGVec_3d shearZ = new SGVec_3d(); 
		this.setToShearZBase(shearZ); 
		shearZ.normalize();
		this.setShearZBaseTo(shearZ, true);
		scaleZBy(scale);
	}

	public void scaleXBy(double scale) {
		SGVec_3d shearX = new SGVec_3d(); 
		setToShearXBase(shearX); 
		double clampedScale = clamp(shearX.mag()*scale);
		shearX.normalize();
		shearX.mult(clampedScale);
		this.setShearXBaseTo(shearX, true);
		this.updateHeadings();

	}

	public void scaleYBy(double scale) {
		SGVec_3d shearY = new SGVec_3d(); 
		setToShearYBase(shearY); 
		double clampedScale = clamp(shearY.mag()*scale);
		shearY.normalize();
		shearY.mult(clampedScale);
		this.setShearYBaseTo(shearY, true);
		this.updateHeadings();
	}

	public void scaleZBy(double scale) {
		SGVec_3d shearZ = new SGVec_3d(); 
		setToShearZBase(shearZ); 
		double clampedScale = clamp(shearZ.mag()*scale);
		shearZ.normalize();
		shearZ.mult(clampedScale);
		this.setShearZBaseTo(shearZ, true);
		this.updateHeadings();
	}


	public SGVec_3d getXHeading() {
		this.setToComposedXBase(scaledXHeading);
		return scaledXHeading;
	}

	public SGVec_3d getYHeading() {
		this.setToComposedYBase(scaledYHeading);
		return scaledYHeading;
	}

	public SGVec_3d getZHeading() {
		this.setToComposedZBase(scaledZHeading);
		return scaledZHeading;
	}

	public void setXHeading(SGVec_3d newXHeading, boolean refreshMatrices) {
		double xHeadingMag = newXHeading.mag();
		xHeadingMag = clamp(xHeadingMag); 
		SGVec_3d modifiedXHeading = newXHeading.copy();
		modifiedXHeading.normalize(); modifiedXHeading.mult(xHeadingMag);
		rotation.applyInverseTo(modifiedXHeading, modifiedXHeading);
		this.setShearXBaseTo(modifiedXHeading, refreshMatrices);
	}

	public void setYHeading(SGVec_3d newYHeading, boolean refreshMatrices) {
		double yHeadingMag = newYHeading.mag();
		yHeadingMag = clamp(yHeadingMag); 
		SGVec_3d modifiedYHeading = newYHeading.copy();
		modifiedYHeading.normalize(); modifiedYHeading.mult(yHeadingMag);
		rotation.applyInverseTo(modifiedYHeading, modifiedYHeading);	 		
		this.setShearYBaseTo(modifiedYHeading, refreshMatrices);
	}

	public void setZHeading(SGVec_3d newZHeading, boolean refreshMatrices) {
		double zHeadingMag = newZHeading.mag();
		zHeadingMag = clamp(zHeadingMag); 
		SGVec_3d modifiedZHeading = newZHeading.copy();
		modifiedZHeading.normalize(); modifiedZHeading.mult(zHeadingMag);
		rotation.applyInverseTo(modifiedZHeading, modifiedZHeading);	 		
		this.setShearZBaseTo(modifiedZHeading, refreshMatrices);
	}

	public void setXHeading(SGVec_3d newXHeading) { 
		setXHeading(newXHeading, true);
	}

	public void setYHeading(SGVec_3d newYHeading) { 
		setXHeading(newYHeading, true);  
	}


	public void setZHeading(SGVec_3d newZHeading) { 
		setXHeading(newZHeading, true);
	}

	/*public void updateScaledHeadings() {
	setToXBase(scaledXHeading);  		
	setToYBase(scaledYHeading);		
	setToZBase(scaledZHeading);
}*/

	SGVec_3d normXHeading = new SGVec_3d(1,0,0);
	SGVec_3d normYHeading = new SGVec_3d(0,1,0);
	SGVec_3d normZHeading = new SGVec_3d(0,0,1);


	protected double clamp(double val) {
		if(val>= 0)
			return Math.max(val, 0.0000000000001d);
		else 
			return Math.min(val, -0.0000000000001d);
	}

	private sgRayd xRay = new sgRayd(new SGVec_3d(0,0,0), new SGVec_3d(1,0,0)); 
	private sgRayd yRay = new sgRayd(new SGVec_3d(0,0,0), new SGVec_3d(0,1,0)); 
	private sgRayd zRay = new sgRayd(new SGVec_3d(0,0,0), new SGVec_3d(0,0,1)); 

	public sgRayd getXRay() {
		return xRay; 
	}

	public sgRayd getYRay() {
		return yRay; 
	}

	public sgRayd getZRay() {
		return zRay; 
	}

	public SGVec_3d getOrigin() {
		return translate;
	}


	public sgRayd getInverseXRay() {
		SGVec_3d inverseX = new SGVec_3d();
		Matrix4d updatedInverseComposed = this.getInverseComposedMatrix();
		inverseX.setX_(updatedInverseComposed.val[M00]); 
		inverseX.setY_(updatedInverseComposed.val[M10]); 
		inverseX.setZ_(updatedInverseComposed.val[M20]);

		sgRayd inverseXRay = new sgRayd(SGVec_3d.mult(this.translate, -1), null); 
		inverseXRay.heading(inverseX);

		return inverseXRay; 
	}

	public sgRayd getInverseYRay() {
		SGVec_3d inverseY = new SGVec_3d();
		Matrix4d updatedInverseComposed = this.getInverseComposedMatrix();
		inverseY.setX_(updatedInverseComposed.val[M01]); 
		inverseY.setY_(updatedInverseComposed.val[M11]); 
		inverseY.setZ_(updatedInverseComposed.val[M21]);

		sgRayd inverseYRay = new sgRayd(SGVec_3d.mult(this.translate, -1), null); 
		inverseYRay.heading(inverseY);
		return inverseYRay; 
	}

	public sgRayd getInverseZRay() {
		SGVec_3d inverseZ = new SGVec_3d();
		Matrix4d updatedInverseComposed = this.getInverseComposedMatrix();
		inverseZ.setX_(updatedInverseComposed.val[M02]); 
		inverseZ.setY_(updatedInverseComposed.val[M12]); 
		inverseZ.setZ_(updatedInverseComposed.val[M22]);
		sgRayd inverseZRay = new sgRayd(SGVec_3d.mult(this.translate, -1), null); 
		inverseZRay.heading(inverseZ);
		return inverseZRay; 
	}

	SGVec_3d tempScale = new SGVec_3d();
	double[][] rotMat = new double[4][4];


	



	/**sets the input Tuple3d to have the values
	 * of this matrix's xbasis 
	 */
	public void setToComposedXBase(SGVec_3d vec){
		vec.setX_(composedMatrix.val[M00]);
		vec.setY_(composedMatrix.val[M10]); 
		vec.setZ_(composedMatrix.val[M20]);
	}

	/**sets the input Tuple3d to have the values
	 * of this matrix's pre-rotation ybasis 
	 */
	public void setToComposedYBase(SGVec_3d vec){
		vec.setX_(composedMatrix.val[M01]); 
		vec.setY_(composedMatrix.val[M11]); 
		vec.setZ_(composedMatrix.val[M21]);
	}

	/**sets the input Tuple3d to have the values
	 * of this matrix's pre-rotation zbasis 
	 */
	public void setToComposedZBase(SGVec_3d vec){
		vec.setX_(composedMatrix.val[M02]); 
		vec.setY_(composedMatrix.val[M12]); 
		vec.setZ_(composedMatrix.val[M22]);
	}


	/**sets the input Tuple3d to have the values
	 * of this matrix's xbasis 
	 */
	public void setToShearXBase(SGVec_3d vec){
		vec.setX_(shearScaleMatrix.val[M00]);
		vec.setY_(shearScaleMatrix.val[M10]); 
		vec.setZ_(shearScaleMatrix.val[M20]);
	}

	/**sets the input Tuple3d to have the values
	 * of this matrix's pre-rotation ybasis 
	 */
	public void setToShearYBase(SGVec_3d vec){
		vec.setX_(shearScaleMatrix.val[M01]); 
		vec.setY_(shearScaleMatrix.val[M11]); 
		vec.setZ_(shearScaleMatrix.val[M21]);
	}

	/**sets the input Tuple3d to have the values
	 * of this matrix's pre-rotation zbasis 
	 */
	public void setToShearZBase(SGVec_3d vec){
		vec.setX_(shearScaleMatrix.val[M02]); 
		vec.setY_(shearScaleMatrix.val[M12]); 
		vec.setZ_(shearScaleMatrix.val[M22]);
	}

	/**sets the matrix's xbasis according to this vector. 
	 * @param compose if true, the cached data for this Basis is recomputed after setting the matrix.  
	 */
	public void setShearXBaseTo(SGVec_3d vec, boolean compose){
		shearScaleMatrix.val[M00] = vec.x; 
		shearScaleMatrix.val[M10] = vec.y; 
		shearScaleMatrix.val[M20] = vec.z;
		if(compose) {
			refreshMatrices();
		}
	}

	/**sets the matrix's ybasis according to this vector. 
	 * @param compose if true, the cached data for this Basis is recomputed after setting the matrix.  
	 */
	public void setShearYBaseTo(SGVec_3d vec, boolean compose){
		shearScaleMatrix.val[M01] = vec.x; 
		shearScaleMatrix.val[M11] = vec.y; 
		shearScaleMatrix.val[M21] = vec.z;
		if(compose) {
			refreshMatrices();
		}
	}

	/**sets the matrix's zbasis according to this vector. 
	 * @param compose if true, the cached data for this Basis is recomputed after setting the matrix.  
	 */
	public void setShearZBaseTo(SGVec_3d vec, boolean compose){
		shearScaleMatrix.val[M02] = vec.x; 
		shearScaleMatrix.val[M12] = vec.y; 
		shearScaleMatrix.val[M22] = vec.z;
		if(compose) {
			refreshMatrices();
		}
	}


	public void updateHeadings() {
		scaledXHeading.x = composedMatrix.val[M00];
		scaledXHeading.y = composedMatrix.val[M10];
		scaledXHeading.z = composedMatrix.val[M20];

		scaledYHeading.x = composedMatrix.val[M01];
		scaledYHeading.y = composedMatrix.val[M11];
		scaledYHeading.z = composedMatrix.val[M21];

		scaledZHeading.x = composedMatrix.val[M02];
		scaledZHeading.y = composedMatrix.val[M12];
		scaledZHeading.z = composedMatrix.val[M22];

		/*setToShearXBase(workingVector);
	if(xBase.dot(workingVector) < 0) flippedAxes[X] = true;
	else flippedAxes[X] = false;

	setToShearYBase(workingVector);
	if(yBase.dot(workingVector) < 0) flippedAxes[Y] = true;
	else flippedAxes[Y] = false;

	setToShearZBase(workingVector);
	if(zBase.dot(workingVector) < 0) flippedAxes[Z] = true;
	else flippedAxes[Z] = false;*/

	}


	Quaternion tempQuat = new Quaternion();
	public void refreshMatrices() {

		//this.shearScaleTransform.set(shearScaleMatrix);
		applyRotTo(this.rotation, this.shearScaleMatrix, this.composedMatrix);
		//this.composedTransform.set(composedMatrix);
		//int oldDeterminant = this.chirality;

		if(this.composedMatrix.determinant() > 0)
			this.chirality = RIGHT; 
		else this.chirality = LEFT;
		this.updateHeadings();
		this.updateRays();
		//if(oldDeterminant != this.chirality)
		this.updateChirality();
		applyRotTo(this.rotation, this.reflectionMatrix, this.composedOrthoNormalMatrix);
		//this.composedOrthonormalTransform.set(composedOrthoNormalMatrix);
		inversesDirty = true;
	}

	public void applyRotTo(Rot rotation, Matrix4d inputMatrix, Matrix4d outputMatrix) {		

		inputMatrix.getColumn(X, arrVec1); 
		rotation.rotation.applyTo(arrVec1, arrVec2);
		outputMatrix.setColumn(X, arrVec2);

		inputMatrix.getColumn(Y, arrVec1); 
		rotation.rotation.applyTo(arrVec1, arrVec2);
		outputMatrix.setColumn(Y, arrVec2);

		inputMatrix.getColumn(Z, arrVec1); 
		rotation.rotation.applyTo(arrVec1, arrVec2);
		outputMatrix.setColumn(Z, arrVec2);

		outputMatrix.val[M33] = 1;
	}


	private void applyInverseRotTo(Rot rotation, Matrix4d inputMatrix, Matrix4d outputMatrix) {
		inputMatrix.getColumn(X, arrVec1); 
		rotation.rotation.applyInverseTo(arrVec1, arrVec2);
		outputMatrix.setColumn(X, arrVec2);

		inputMatrix.getColumn(Y, arrVec1); 
		rotation.rotation.applyInverseTo(arrVec1, arrVec2);
		outputMatrix.setColumn(Y, arrVec2);

		inputMatrix.getColumn(Z, arrVec1); 
		rotation.rotation.applyInverseTo(arrVec1, arrVec2);
		outputMatrix.setColumn(Z, arrVec2);

		outputMatrix.val[M33] = 1;
	}

	double [] arrVec1 = new double[4];
	double [] arrVec2 = new double[4];

	private void updateChirality() {
		setFlipArrayForMatrix(this.composedMatrix, this.flippedAxes, this.rotation);
		arrVec1[X] = flippedAxes[X] ? -1 : 1; arrVec1[Y] = 0; arrVec1[Z] = 0; arrVec1[3] = 0;
		reflectionMatrix.setColumn(0, arrVec1);

		arrVec1[X] = 0; arrVec1[Y] = flippedAxes[Y] ? -1 : 1; arrVec1[Z] = 0;
		reflectionMatrix.setColumn(1, arrVec1);

		arrVec1[X] = 0; arrVec1[Y] = 0; arrVec1[Z]= flippedAxes[Z] ? -1 : 1;
		reflectionMatrix.setColumn(2, arrVec1);

		reflectionMatrix.val[M33] = 1;	
	}

	public void setFlipArrayForMatrix(Matrix4d forMatrix, boolean[] flipArray, Rot rotation) {
		SGVec_3d tempV = new SGVec_3d();
		double[] vecArr = new double[4]; 
		forMatrix.getColumn(Z, vecArr); 
		tempV.x = vecArr[X]; tempV.y = vecArr[Y]; tempV.z =  vecArr[Z];
		forMatrix.getColumn(Y, vecArr); 
		tempV.x = vecArr[X]; tempV.y = vecArr[Y]; tempV.z =  vecArr[Z];
		SGVec_3d tempVec = new SGVec_3d(); 
		rotation.applyTo(xBase, tempVec);		
		forMatrix.getColumn(X, vecArr); 
		tempV.x = vecArr[X]; tempV.y = vecArr[Y]; tempV.z =  vecArr[Z];		

		double dot = tempVec.dot(tempV);
		if( dot < 0) {		
			flipArray[X] = true;
		} else {
			flipArray[X] = false;
		}

		forMatrix.getColumn(Y, vecArr); 
		tempV.x = vecArr[X]; tempV.y = vecArr[Y]; tempV.z =  vecArr[Z];
		rotation.applyTo(yBase, tempVec);
		if(tempVec.dot(tempV) < 0) {
			flipArray[Y] = true;
		}
		else flipArray[Y] = false;

		forMatrix.getColumn(Z, vecArr); 
		tempV.x = vecArr[X]; tempV.y = vecArr[Y]; tempV.z =  vecArr[Z];
		rotation.applyTo(zBase, tempVec);
		if(tempVec.dot(tempV) < 0) {
			flipArray[Z] = true;
		}
		else flipArray[Z] = false;		
	}

	//SGVec_3d tempV_1 = new SGVec_3d();

	/*public void setDVecFromTuple(SGVec_3d vec, SGVec_3d tuple) {
		vec.x = tuple.x; 
		vec.y = tuple.y; 
		vec.z = tuple.z;
	}

	public void setTupleFromDVec(SGVec_3d vec, SGVec_3d tuple) {
		tuple.x = vec.x; tuple.y = vec.y; tuple.z = vec.z;
	}*/

	private void updateRays() {		
		SGVec_3d tempV = new SGVec_3d();
		xRay.setP1(this.translate); 		
		yRay.setP1(this.translate);			
		zRay.setP1(this.translate);

		setToComposedXBase(tempV);
		xRay.heading(tempV);
		setToComposedYBase(tempV);
		yRay.heading(tempV);
		setToComposedZBase(tempV);
		zRay.heading(tempV);		
	}


	/*public String typeString(Transform3D input) {
	if(input == null) input = composedTransform;
	int type = input.getBestType();
	String result = "";
	if ((type & Transform3D.ZERO)             	  > 0 ) result += " ZERO";
	if ((type & Transform3D.IDENTITY)             > 0 ) result +=" IDENTITY";
	if ((type & Transform3D.SCALE)                > 0 ) result +=" SCALE";
	if ((type & Transform3D.TRANSLATION)          > 0 ) result +=" TRANSLATION";
	if ((type & Transform3D.ORTHOGONAL)           > 0 ) result +=" ORTHOGONAL";
	if ((type & Transform3D.RIGID)                > 0 ) result +=" RIGID";
	if ((type & Transform3D.CONGRUENT)            > 0 ) result +=" CONGRUENT";
	if ((type & Transform3D.AFFINE)               > 0 ) result +=" AFFINE";
	if ((type & Transform3D.NEGATIVE_DETERMINANT) > 0 ) result +=" NEGATIVE_DETERMINANT";

	return result;
}*/

	public void setIdentity() {
		this.scale.x = 1; this.scale.y = 1; this.scale.z = 1; 
		this.scaledXHeading = xBase.copy();
		this.scaledYHeading = yBase.copy(); 
		this.scaledZHeading = zBase.copy();
		this.rotation = new Rot();
		this.translate.x = 0; this.translate.y = 0; this.translate.z = 0;
		this.composedMatrix.idt();
		this.shearScaleMatrix.idt();

		refreshMatrices();
	}



	public void createMultiDimMatrixFromMat3d(Matrix3d mat3d, double[][] outputMultiDimMatrix) {
		outputMultiDimMatrix[0][0] = mat3d.val[M00];   outputMultiDimMatrix[1][0] = mat3d.val[M01];  outputMultiDimMatrix[2][0] = mat3d.val[M02];
		outputMultiDimMatrix[0][1] = mat3d.val[M10];   outputMultiDimMatrix[1][1] = mat3d.val[M11];  outputMultiDimMatrix[2][1] = mat3d.val[M12];
		outputMultiDimMatrix[0][2] = mat3d.val[M20];   outputMultiDimMatrix[1][2] = mat3d.val[M21];  outputMultiDimMatrix[2][2] = mat3d.val[M22];		
	}

	public void createMultiDimMatrixFromMat4d(Matrix4d mat4d, double[][] outputMultiDimMatrix) {
		outputMultiDimMatrix[0][0] = mat4d.val[M00];   outputMultiDimMatrix[1][0] = mat4d.val[M01];  outputMultiDimMatrix[2][0] = mat4d.val[M02];
		outputMultiDimMatrix[0][1] = mat4d.val[M10];   outputMultiDimMatrix[1][1] = mat4d.val[M11];  outputMultiDimMatrix[2][1] = mat4d.val[M12];
		outputMultiDimMatrix[0][2] = mat4d.val[M20];   outputMultiDimMatrix[1][2] = mat4d.val[M21];  outputMultiDimMatrix[2][2] = mat4d.val[M22];		
	}

	public void createMat3dFromMultiDimMatrix(double[][] multiDimMatrix, Matrix3d outputMat3d) {

		outputMat3d.val[M00] = multiDimMatrix[0][0];  outputMat3d.val[M01] = multiDimMatrix[1][0];  outputMat3d.val[M02] = multiDimMatrix[2][0]; 
		outputMat3d.val[M10] = multiDimMatrix[0][1];  outputMat3d.val[M11] = multiDimMatrix[1][1];  outputMat3d.val[M12] = multiDimMatrix[2][1]; 
		outputMat3d.val[M20] = multiDimMatrix[0][2];  outputMat3d.val[M21] = multiDimMatrix[1][2];  outputMat3d.val[M22] = multiDimMatrix[2][2]; 

	}

	public void createMat4dFromMultiDimMatrix(double[][] multiDimMatrix, Matrix4d outputMat4d) {

		outputMat4d.val[M00] = multiDimMatrix[0][0];  outputMat4d.val[M01] = multiDimMatrix[1][0];  outputMat4d.val[M02] = multiDimMatrix[2][0]; 
		outputMat4d.val[M10] = multiDimMatrix[0][1];  outputMat4d.val[M11] = multiDimMatrix[1][1];  outputMat4d.val[M12] = multiDimMatrix[2][1]; 
		outputMat4d.val[M20] = multiDimMatrix[0][2];  outputMat4d.val[M21] = multiDimMatrix[1][2];  outputMat4d.val[M22] = multiDimMatrix[2][2]; 
		outputMat4d.val[M33] = 1;

	}

	public Matrix4d getComposedMatrix() {
		return composedMatrix;
	}

	public Matrix4d getInverseComposedMatrix() {
		if(inversesDirty) {
			this.updateInverses();
			inversesDirty = false;			
		}
		return this.inverseComposedMatrix;
	}

	public Matrix4d getInverseReflectionMatrix() {
		if(inversesDirty) {
			this.updateInverses();
			inversesDirty = false;			
		}
		return this.inverseReflectionMatrix;
	}


	private Matrix4d getInverseComposedOrthoNormalMatrix() {
		if(inversesDirty) {
			this.updateInverses();
			inversesDirty = false;			
		}
		return this.inverseComposedOrthoNormalMatrix;
	}


	public void updateInverses() {

		this.inverseComposedMatrix.toInverseOf(composedMatrix);
		//this.inverseComposedTransform.set(inverseComposedMatrix);
		this.inverseReflectionMatrix.toInverseOf(this.reflectionMatrix);
		this.inverseComposedOrthoNormalMatrix.toInverseOf(composedOrthoNormalMatrix);
		//this.inverseComposedOrthonormalTransform.set(inverseComposedOrthoNormalMatrix);

	}

	public Matrix4d getShearScaleMatrix() {
		return this.shearScaleMatrix;
	}

	public static final int 
	M00 = 0, M01 = 4, M02 = 8, M03 = 1, 
	M10 = 1, M11 = 5, M12 = 9, M13 = 13,
	M20 = 2, M21 = 6, M22 = 10, M23 = 14, 
	M30 = 3, M31 = 7, M32 = 11, M33 = 15;


	public static final int 
	mM00 = 0, mM01 = 3, mM02 = 6,
	mM10 = 1, mM11 = 4, mM12 = 7, 
	mM20 = 2, mM21 = 5, mM22 = 8; 

}