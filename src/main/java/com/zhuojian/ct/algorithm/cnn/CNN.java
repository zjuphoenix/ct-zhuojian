package com.zhuojian.ct.algorithm.cnn;

import com.zhuojian.ct.algorithm.cnn.DataPreProcess.DcmConstant;
import com.zhuojian.ct.dicom.DicomReader;
import com.zhuojian.ct.dicom.DicomReaderImpl;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class CNN implements Serializable {
	private static final long serialVersionUID = 337920299147929932L;
	private static double ALPHA = 0.85;
	protected static final double LAMBDA = 0;
	private List<Layer> layers;
	private int layerNum;

	private int batchSize;
	private Util.Operator divide_batchSize;
	private Util.Operator multiply_alpha;
	private Util.Operator multiply_lambda;


	public CNN(LayerBuilder layerBuilder, final int batchSize) {
		layers = layerBuilder.getmLayers();
		layerNum = layers.size();
		this.batchSize = batchSize;
		setup(batchSize);
		initPerator();
	}


	private void initPerator() {
		divide_batchSize = new Util.Operator() {

			private static final long serialVersionUID = 7424011281732651055L;

			@Override
			public double process(double value) {
				return value / batchSize;
			}

		};
		multiply_alpha = new Util.Operator() {

			private static final long serialVersionUID = 5761368499808006552L;

			@Override
			public double process(double value) {

				return value * ALPHA;
			}

		};
		multiply_lambda = new Util.Operator() {

			private static final long serialVersionUID = 4499087728362870577L;

			@Override
			public double process(double value) {

				return value * (1 - LAMBDA * ALPHA);
			}

		};
	}

	public void train(DataSet trainset, int repeat) {
		new Lisenter().start();
		for (int t = 0; t < repeat && !stopTrain.get(); t++) {
			int epochsNum = trainset.size() / batchSize;
			if (trainset.size() % batchSize != 0)
				epochsNum++;
			Log.i("");
			Log.i(t + "th iter epochsNum:" + epochsNum);
			int right = 0;
			int count = 0;
			for (int i = 0; i < epochsNum; i++) {
				int[] randPerm = Util.randomPerm(trainset.size(), batchSize);
				Layer.prepareForNewBatch();

				for (int index : randPerm) {
					boolean isRight = train(trainset.getRecord(index));
					if (isRight)
						right++;
					count++;
					Layer.prepareForNewRecord();
				}

				updateParas();
				if (i % 50 == 0) {
					System.out.print("..");
					if (i + 50 > epochsNum)
						System.out.println();
				}
			}
			double p = 1.0 * right / count;
			if (t % 10 == 1 && p > 0.96) {
				ALPHA = 0.001 + ALPHA * 0.9;
				Log.i("Set alpha = " + ALPHA);
			}
			Log.i("precision " + right + "/" + count + "=" + p);
		}
	}

	// train dicom cnn
	public void train(List<int[]> tra, int repeat, String path) {
		new Lisenter().start();
		Map<String, double[]> dataMap = null;
		try {
			dataMap = dcms(path);
		} catch (Exception e) {
			e.printStackTrace();
		}
		if (dataMap == null)
			throw new RuntimeException("data map error.");
		for (int t = 0; t < repeat && !stopTrain.get(); t++) {
			int epochsNum = tra.size() / batchSize;
			if (tra.size() % batchSize != 0)
				epochsNum++;
			Log.i("");
			Log.i(t + "th iter epochsNum:" + epochsNum);
			int right = 0;
			int count = 0;
			for (int i = 0; i < epochsNum; i++) {
				int[] randPerm = Util.randomPerm(tra.size(), batchSize);
				Layer.prepareForNewBatch();

				for (int index : randPerm) {
                    int[] ori = tra.get(index);
                    try {
						int subDir = ori[0];
						int suffix = ori[1];
						int from = ori[2];
						double label = ori[3];
						String newPath = path + "/" + from + "/" + subDir + "/" + suffix;
//						String newPath = path + "\\" + from + "\\" + subDir + "/" + suffix;
						double[] data = dataMap.get(newPath);
						if (data == null || data.length != 128*128) {
							System.out.println(data.length);
							throw new RuntimeException("data length is not 128*128.");
						}
                        //Record record = getDcmRecord(ori, path, dicomReader);
						Record record = new Record(data, label);
                        boolean isRight = train(record);
                        if (isRight)
                            right++;
                        count++;
                        Layer.prepareForNewRecord();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
				}
				updateParas();
				if (i % 50 == 0) {
					System.out.print("..");
					if (i + 50 > epochsNum)
						System.out.println();
				}
			}
			double p = 1.0 * right / count;
			if (t % 10 == 1 && p > 0.96) {
				ALPHA = 0.001 + ALPHA * 0.9;
				Log.i("Set alpha = " + ALPHA);
			}
			Log.i("precision " + right + "/" + count + "=" + p);
		}
	}

	private static AtomicBoolean stopTrain;

	static class Lisenter extends Thread {
		Lisenter() {
			setDaemon(true);
			stopTrain = new AtomicBoolean(false);
		}

		@Override
		public void run() {
			System.out.println("Input & to stop train.");
			while (true) {
				try {
					int a = System.in.read();
					if (a == '&') {
						stopTrain.compareAndSet(false, true);
						break;
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			System.out.println("Lisenter stop");
		}

	}

	public double test(DataSet trainset) {
		Layer.prepareForNewBatch();
		Iterator<Record> iter = trainset.iter();
		int right = 0;
		while (iter.hasNext()) {
			Record record = iter.next();
			forward(record);
			Layer outputLayer = layers.get(layerNum - 1);
			int mapNum = outputLayer.getOutMapNum();		
			double[] out = new double[mapNum];
			for (int m = 0; m < mapNum; m++) {
				double[][] outmap = outputLayer.getMap(m);
				out[m] = outmap[0][0];
			}
			if (record.getLable().intValue() == Util.getMaxIndex(out))
				right++;		
		}
		double p = 1.0 * right / trainset.size();
		Log.i("precision", p + "");
		return p;
	}


	public void predict(DataSet testset, String fileName) {
        Log.i("begin predict");
        try {
            int max = layers.get(layerNum - 1).getClassNum();
            PrintWriter writer = new PrintWriter(new File(fileName));
            Layer.prepareForNewBatch();
            Iterator<Record> iter = testset.iter();
            while (iter.hasNext()) {
                Record record = iter.next();
                forward(record);
                Layer outputLayer = layers.get(layerNum - 1);

                int mapNum = outputLayer.getOutMapNum();
                double[] out = new double[mapNum];
                for (int m = 0; m < mapNum; m++) {
                    double[][] outmap = outputLayer.getMap(m);
                    out[m] = outmap[0][0];
                }
                // int lable =
                // Util.binaryArray2int(out);
                int lable = Util.getMaxIndex(out);
                // if (lable >= max)
                // lable = lable - (1 << (out.length -
                // 1));
                writer.write(lable + "\n");
            }
            writer.flush();
            writer.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        Log.i("end predict");
    }

    // predict by jql
    public int predict(double[] data) {
        Log.i("begin predict");
        int label = -1;
//        int max = layers.get(layerNum - 1).getClassNum();
        Layer.prepareForNewBatch();
        forward(new Record(data));
        Layer outputLayer = layers.get(layerNum - 1);

        int mapNum = outputLayer.getOutMapNum();
        double[] out = new double[mapNum];
        for (int m = 0; m < mapNum; m++) {
            double[][] outmap = outputLayer.getMap(m);
            out[m] = outmap[0][0];
        }
        label = Util.getMaxIndex(out);
        Log.i("end predict");
        return label;
    }

	private boolean isSame(double[] output, double[] target) {
		boolean r = true;
		for (int i = 0; i < output.length; i++)
			if (Math.abs(output[i] - target[i]) > 0.5) {
				r = false;
				break;
			}

		return r;
	}

	private boolean train(Record record) {
		forward(record);
		boolean result = backPropagation(record);
		return result;
		// System.exit(0);
	}


	private boolean backPropagation(Record record) {
		boolean result = setOutLayerErrors(record);
		setHiddenLayerErrors();
		return result;
	}


	private void updateParas() {
		for (int l = 1; l < layerNum; l++) {
			Layer layer = layers.get(l);
			Layer lastLayer = layers.get(l - 1);
			switch (layer.getType()) {
			case conv:
			case output:
				updateKernels(layer, lastLayer);
				updateBias(layer, lastLayer);
				break;
			default:
				break;
			}
		}
	}

	private void updateBias(final Layer layer, Layer lastLayer) {
		final double[][][][] errors = layer.getErrors();
		int mapNum = layer.getOutMapNum();

		new ConcurenceRunner.TaskManager(mapNum) {

			@Override
			public void process(int start, int end) {
				for (int j = start; j < end; j++) {
					double[][] error = Util.sum(errors, j);
					double deltaBias = Util.sum(error) / batchSize;
					double bias = layer.getBias(j) + ALPHA * deltaBias;
					layer.setBias(j, bias);
				}
			}
		}.start();

	}


	private void updateKernels(final Layer layer, final Layer lastLayer) {
		int mapNum = layer.getOutMapNum();
		final int lastMapNum = lastLayer.getOutMapNum();
		new ConcurenceRunner.TaskManager(mapNum) {

			@Override
			public void process(int start, int end) {
				for (int j = start; j < end; j++) {
					for (int i = 0; i < lastMapNum; i++) {
						// ��batch��ÿ����¼delta���
						double[][] deltaKernel = null;
						for (int r = 0; r < batchSize; r++) {
							double[][] error = layer.getError(r, j);
							if (deltaKernel == null)
								deltaKernel = Util.convnValid(
										lastLayer.getMap(r, i), error);
							else {// �ۻ����
								deltaKernel = Util.matrixOp(Util.convnValid(
										lastLayer.getMap(r, i), error),
										deltaKernel, null, null, Util.plus);
							}
						}

						// ����batchSize
						deltaKernel = Util.matrixOp(deltaKernel,
								divide_batchSize);
						// ���¾����
						double[][] kernel = layer.getKernel(i, j);
						deltaKernel = Util.matrixOp(kernel, deltaKernel,
								multiply_lambda, multiply_alpha, Util.plus);
						layer.setKernel(i, j, deltaKernel);
					}
				}

			}
		}.start();

	}

	private void setHiddenLayerErrors() {
		for (int l = layerNum - 2; l > 0; l--) {
			Layer layer = layers.get(l);
			Layer nextLayer = layers.get(l + 1);
			switch (layer.getType()) {
			case samp:
				setSampErrors(layer, nextLayer);
				break;
			case conv:
				setConvErrors(layer, nextLayer);
				break;
			default:
				break;
			}
		}
	}

	private void setSampErrors(final Layer layer, final Layer nextLayer) {
		int mapNum = layer.getOutMapNum();
		final int nextMapNum = nextLayer.getOutMapNum();
		new ConcurenceRunner.TaskManager(mapNum) {

			@Override
			public void process(int start, int end) {
				for (int i = start; i < end; i++) {
					double[][] sum = null;// ��ÿһ������������
					for (int j = 0; j < nextMapNum; j++) {
						double[][] nextError = nextLayer.getError(j);
						double[][] kernel = nextLayer.getKernel(i, j);
						// �Ծ���˽���180����ת��Ȼ�����fullģʽ�µþ��
						if (sum == null)
							sum = Util
									.convnFull(nextError, Util.rot180(kernel));
						else
							sum = Util.matrixOp(
									Util.convnFull(nextError,
											Util.rot180(kernel)), sum, null,
									null, Util.plus);
					}
					layer.setError(i, sum);
				}
			}

		}.start();

	}

	private void setConvErrors(final Layer layer, final Layer nextLayer) {
		// ��������һ��Ϊ�����㣬�������map������ͬ����һ��mapֻ����һ���һ��map���ӣ�
		// ���ֻ�轫��һ��Ĳв�kronecker��չ���õ������
		int mapNum = layer.getOutMapNum();
		new ConcurenceRunner.TaskManager(mapNum) {

			@Override
			public void process(int start, int end) {
				for (int m = start; m < end; m++) {
					Layer.Size scale = nextLayer.getScaleSize();
					double[][] nextError = nextLayer.getError(m);
					double[][] map = layer.getMap(m);
					// ������ˣ����Եڶ��������ÿ��Ԫ��value����1-value����
					double[][] outMatrix = Util.matrixOp(map,
							Util.cloneMatrix(map), null, Util.one_value,
							Util.multiply);
					outMatrix = Util.matrixOp(outMatrix,
							Util.kronecker(nextError, scale), null, null,
							Util.multiply);
					layer.setError(m, outMatrix);
				}

			}

		}.start();

	}

	private boolean setOutLayerErrors(Record record) {

		Layer outputLayer = layers.get(layerNum - 1);
		int mapNum = outputLayer.getOutMapNum();
		// double[] target =
		// record.getDoubleEncodeTarget(mapNum);
		// double[] outmaps = new double[mapNum];
		// for (int m = 0; m < mapNum; m++) {
		// double[][] outmap = outputLayer.getMap(m);
		// double output = outmap[0][0];
		// outmaps[m] = output;
		// double errors = output * (1 - output) *
		// (target[m] - output);
		// outputLayer.setError(m, 0, 0, errors);
		// }
		// // ��ȷ
		// if (isSame(outmaps, target))
		// return true;
		// return false;

		double[] target = new double[mapNum];
		double[] outmaps = new double[mapNum];
		for (int m = 0; m < mapNum; m++) {
			double[][] outmap = outputLayer.getMap(m);
			outmaps[m] = outmap[0][0];

		}
		int lable = record.getLable().intValue();
		target[lable] = 1;
		// Log.i(record.getLable() + "outmaps:" +
		// Util.fomart(outmaps)
		// + Arrays.toString(target));
		for (int m = 0; m < mapNum; m++) {
			outputLayer.setError(m, 0, 0, outmaps[m] * (1 - outmaps[m])
					* (target[m] - outmaps[m]));
		}
		return lable == Util.getMaxIndex(outmaps);
	}

	private void forward(Record record) {
		setInLayerOutput(record);
		for (int l = 1; l < layers.size(); l++) {
			Layer layer = layers.get(l);
			Layer lastLayer = layers.get(l - 1);
			switch (layer.getType()) {
			case conv:// ������������
				setConvOutput(layer, lastLayer);
				break;
			case samp:// �������������
				setSampOutput(layer, lastLayer);
				break;
			case output:// �������������,�������һ������ľ����
				setConvOutput(layer, lastLayer);
				break;
			default:
				break;
			}
		}
	}

	private void setInLayerOutput(Record record) {
		final Layer inputLayer = layers.get(0);
		final Layer.Size mapSize = inputLayer.getMapSize();
		final double[] attr = record.getAttrs();
		if (attr.length != mapSize.x * mapSize.y)
			throw new RuntimeException("���ݼ�¼�Ĵ�С�붨���map��С��һ��!");
		for (int i = 0; i < mapSize.x; i++) {
			for (int j = 0; j < mapSize.y; j++) {
				// ����¼���Ե�һά����Ū�ɶ�ά����
				inputLayer.setMapValue(0, i, j, attr[mapSize.x * i + j]);
			}
		}
	}

	/*
	 * �����������ֵ,ÿ���̸߳���һ����map
	 */
	private void setConvOutput(final Layer layer, final Layer lastLayer) {
		int mapNum = layer.getOutMapNum();
		final int lastMapNum = lastLayer.getOutMapNum();
		new ConcurenceRunner.TaskManager(mapNum) {

			@Override
			public void process(int start, int end) {
				for (int j = start; j < end; j++) {
					double[][] sum = null;// ��ÿһ������map�ľ���������
					for (int i = 0; i < lastMapNum; i++) {
						double[][] lastMap = lastLayer.getMap(i);
						double[][] kernel = layer.getKernel(i, j);
						if (sum == null)
							sum = Util.convnValid(lastMap, kernel);
						else
							sum = Util.matrixOp(
									Util.convnValid(lastMap, kernel), sum,
									null, null, Util.plus);
					}
					final double bias = layer.getBias(j);
					sum = Util.matrixOp(sum, new Util.Operator() {
						private static final long serialVersionUID = 2469461972825890810L;

						@Override
						public double process(double value) {
							return Util.sigmod(value + bias);
						}

					});

					layer.setMapValue(j, sum);
				}
			}

		}.start();

	}

	/**
	 * ���ò���������ֵ���������ǶԾ����ľ�ֵ����
	 * 
	 * @param layer
	 * @param lastLayer
	 */
	private void setSampOutput(final Layer layer, final Layer lastLayer) {
		int lastMapNum = lastLayer.getOutMapNum();
		new ConcurenceRunner.TaskManager(lastMapNum) {

			@Override
			public void process(int start, int end) {
				for (int i = start; i < end; i++) {
					double[][] lastMap = lastLayer.getMap(i);
					Layer.Size scaleSize = layer.getScaleSize();
					// ��scaleSize������о�ֵ����
					double[][] sampMatrix = Util
							.scaleMatrix(lastMap, scaleSize);
					layer.setMapValue(i, sampMatrix);
				}
			}

		}.start();

	}

	/**
	 * ����cnn�����ÿһ��Ĳ���
	 * 
	 * @param batchSize
	 *            * @param classNum
	 */
	public void setup(int batchSize) {
		Layer inputLayer = layers.get(0);
		// ÿһ�㶼��Ҫ��ʼ�����map
		inputLayer.initOutmaps(batchSize);
		for (int i = 1; i < layers.size(); i++) {
			Layer layer = layers.get(i);
			Layer frontLayer = layers.get(i - 1);
			int frontMapNum = frontLayer.getOutMapNum();
			switch (layer.getType()) {
			case input:
				break;
			case conv:
				// ����map�Ĵ�С
				layer.setMapSize(frontLayer.getMapSize().subtract(
						layer.getKernelSize(), 1));
				// ��ʼ������ˣ�����frontMapNum*outMapNum�������

				layer.initKernel(frontMapNum);
				// ��ʼ��ƫ�ã�����frontMapNum*outMapNum��ƫ��
				layer.initBias(frontMapNum);
				// batch��ÿ����¼��Ҫ����һ�ݲв�
				layer.initErros(batchSize);
				// ÿһ�㶼��Ҫ��ʼ�����map
				layer.initOutmaps(batchSize);
				break;
			case samp:
				// �������map��������һ����ͬ
				layer.setOutMapNum(frontMapNum);
				// ������map�Ĵ�С����һ��map�Ĵ�С����scale��С
				layer.setMapSize(frontLayer.getMapSize().divide(
						layer.getScaleSize()));
				// batch��ÿ����¼��Ҫ����һ�ݲв�
				layer.initErros(batchSize);
				// ÿһ�㶼��Ҫ��ʼ�����map
				layer.initOutmaps(batchSize);
				break;
			case output:
				// ��ʼ��Ȩ�أ�����ˣ��������ľ���˴�СΪ��һ���map��С
				layer.initOutputKerkel(frontMapNum, frontLayer.getMapSize());
				// ��ʼ��ƫ�ã�����frontMapNum*outMapNum��ƫ��
				layer.initBias(frontMapNum);
				// batch��ÿ����¼��Ҫ����һ�ݲв�
				layer.initErros(batchSize);
				// ÿһ�㶼��Ҫ��ʼ�����map
				layer.initOutmaps(batchSize);
				break;
			}
		}
	}

	/**
	 * ���л�����ģ��
	 * 
	 * @param fileName
	 */
	public void saveModel(String fileName) {
		try {
			File file = new File(fileName);
			ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file));
//			ObjectOutputStream oos = new ObjectOutputStream(
//					new FileOutputStream(fileName));
			oos.writeObject(this);
			oos.flush();
			oos.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	/**
	 * �����л�����ģ��
	 * 
	 * @param fileName
	 * @return
	 */
	public static CNN loadModel(String fileName) {
		try {
			ObjectInputStream in = new ObjectInputStream(new FileInputStream(
					fileName));
			CNN cnn = (CNN) in.readObject();
			in.close();
			return cnn;
		} catch (IOException | ClassNotFoundException e) {
			e.printStackTrace();
		}
		return null;
	}

    private static Record getDcmRecord(int[] ori, String path, DicomReader dicomReader) throws Exception {
        int subDir = ori[0];
        int suffix = ori[1];
        int from = ori[2];
        double label = ori[3];
        File parent = new File(path + "/" + from + "/" + subDir);
        String absPath = null;
        for (File f : parent.listFiles()) {
            if (f.getName().endsWith("." + suffix + ".dcm")) {
                absPath = f.getAbsolutePath();
                break;
            }
        }
        if (absPath == null) {
            throw new Exception("can not find this dicom files in " + path);
        }
        double[] dat = dicomReader.readTmp128DataInLine(new File(absPath), DcmConstant.XRESIZE, DcmConstant.YRESIZE);
        Record record = new Record(dat, label);
        return record;
    }

	public Map<String, double[]> dcms(String path) throws Exception {
		DicomReader reader = new DicomReaderImpl();
		Map<String, double[]> dataMap = new HashMap<>(1024);
		File parent = new File(path);
//		int count = 0;
		for (File f : parent.listFiles()) {
			String secondPath = f.getAbsolutePath();
			for (File dcmDir : f.listFiles()) {
				String thridPath = dcmDir.getAbsolutePath();
//				count = count + dcmDir.listFiles().length;
//				System.out.println("###@@@" + dcmDir.listFiles().length);
				for (File dcm : dcmDir.listFiles()) {
					String fileName = dcm.getName();
					double[] dat = reader.readTmp128DataInLine(new File(dcm.getAbsolutePath()), DcmConstant.XRESIZE, DcmConstant.YRESIZE);
					String[] req = fileName.split("\\.");
//					System.out.println(thridPath + "/" + req[req.length - 2]);
					dataMap.put(thridPath + "/" + req[req.length - 2], dat);
				}
			}
		}
		System.out.println("sigma:@@@###" + dataMap.size());
		return dataMap;
	}

}
