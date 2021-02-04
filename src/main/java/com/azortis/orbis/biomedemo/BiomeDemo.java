/*
 * MIT License
 *
 * Copyright (c) 2021 Azortis
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.azortis.orbis.biomedemo;

import com.azortis.orbis.biomedemo.noise.OpenSimplex2S;
import com.azortis.orbis.biomedemo.objects.*;
import com.azortis.orbis.biomedemo.objects.Dimension;
import com.azortis.orbis.biomedemo.objects.layer.BiomeLayer;
import com.azortis.orbis.biomedemo.objects.layer.RegionLayer;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

public final class BiomeDemo {

    private static final int WIDTH = 4096;
    private static final int HEIGHT = 1536;
    private static final int CHUNK_WIDTH = 16;

    private static final long REGION_SEED = 1242352482951642511L;
    private static final long TYPE_SEED = 2235650352155145L;
    private static final long SEED = 235623651371436421L;

    private static final int MIN_BLEND_RADIUS = 32;
    private static final double POINT_FREQUENCY = 0.04;
    private static final double REGION_ZOOM = 2500;
    private static final double TYPE_ZOOM = 850;
    private static final double BIOME_ZOOM = 300;

    private static final double LAND_MIN = 0.31;
    private static final double LAND_MAX = 1;
    private static final double SHORE_MIN = 0.15;
    private static final double SHORE_MAX = 0.30;
    private static final double SEA_MIN = -1;
    private static final double SEA_MAX = 0.14;

    private static final OpenSimplex2S regionNoise = new OpenSimplex2S(REGION_SEED);
    private static final OpenSimplex2S typeNoise = new OpenSimplex2S(TYPE_SEED);
    private static final OpenSimplex2S biomeNoise = new OpenSimplex2S(SEED);

    private static Dimension dimension;

    public static void main(String[] args){
        long startTime = System.currentTimeMillis();
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);

        ScatteredBiomeBlender biomeBlender = new ScatteredBiomeBlender(POINT_FREQUENCY, MIN_BLEND_RADIUS, CHUNK_WIDTH);

        List<Long> chunkTimes = new ArrayList<>();

        for (int zc = 0; zc < HEIGHT; zc += CHUNK_WIDTH){
            for(int xc = 0; xc < WIDTH; xc += CHUNK_WIDTH){
                long startChunkTime = System.nanoTime();

                LinkedBiomeWeightMap firstBiomeWeightMap = biomeBlender.getBlendForChunk(SEED, xc, zc, BiomeDemo::getOldBiomeAt);

                for (int zi = 0; zi < CHUNK_WIDTH; zi++) {
                    for (int xi = 0; xi < CHUNK_WIDTH; xi++) {
                        int z = zc + zi;
                        int x = xc + xi;

                        double r, g, b; r = g = b = 0;

                        for (LinkedBiomeWeightMap entry = firstBiomeWeightMap; entry != null; entry = entry.getNext()) {
                            double weight = entry.getWeights()[zi * CHUNK_WIDTH + xi];
                            int biome = entry.getBiome();
                            Biomes biome1 = Biomes.getBiome(biome);
                            assert biome1 != null;
                            Color color = biome1.getColor();
                            r += color.getRed() * weight;
                            g += color.getGreen() * weight;
                            b += color.getBlue() * weight;
                        }

                        int rgb = new Color((int)r, (int)g, (int)b).getRGB();
                        image.setRGB(x, z, rgb);
                    }
                }
                long chunkTime = System.nanoTime() - startChunkTime;
                chunkTimes.add(chunkTime);
            }
        }
        long time = System.currentTimeMillis() - startTime;
        System.out.println("It took the algorithm " + time + "milliseconds to render the image!");

        long totalChunkTime = chunkTimes.stream().mapToLong(aLong -> aLong).sum();
        long avgChunkTime = totalChunkTime / chunkTimes.size();
        System.out.println("With an average chunk time of: " + avgChunkTime + "nanoseconds!");

        JFrame frame = new JFrame();
        JLabel imageLabel = new JLabel();
        imageLabel.setIcon(new ImageIcon(image));
        frame.add(imageLabel);
        frame.setTitle("Orbis Biome Demo");
        frame.pack();
        frame.setResizable(false);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setVisible(true);
    }

    private static int getOldBiomeAt(double x, double z){
        // Get the region
        double regionValue = Math.round(regionNoise.noise(x / REGION_ZOOM, z / REGION_ZOOM) * 100) / 100.0;
        int region = -1;
        if(regionValue <= -0.33)region = 0;
        if(regionValue >= -0.32 && regionValue <= 0.33)region = 1;
        if(regionValue >= 0.34)region = 2;

        // Get the type
        double typeValue = Math.round(typeNoise.noise(x / TYPE_ZOOM, z / TYPE_ZOOM) * 100) / 100.0;
        int type = -1;
        if(typeValue >= LAND_MIN && typeValue <= LAND_MAX)type = 0;
        if(typeValue >= SHORE_MIN && typeValue <= SHORE_MAX)type = 1;
        if(typeValue >= SEA_MIN && typeValue <= SEA_MAX)type = 2;

        if(region == -1 || typeValue == -1) System.out.println("ERROR: Something went wrong with calculation type or region");

        double biomeValue = biomeNoise.noise(x / BIOME_ZOOM, z / BIOME_ZOOM);
        Biomes biome = Biomes.getBiome(region, type, biomeValue);
        assert biome != null;
        return biome.getBiomeId();
    }

    private static int getBiomeAt(double x, double z){
        final OpenSimplex2S noise = new OpenSimplex2S(dimension.getTypeSeed());

        // First calculate the type.
        final double typeNoise = Math.round(noise.noise(x / dimension.getTypeZoom(), z / dimension.getTypeZoom()) *
                dimension.getPrecision()) / dimension.getPrecision();
        int type = -1;
        double typeContext = 0;
        if(typeNoise >= dimension.getLandMin() && typeNoise <= dimension.getLandMax()){
            type = 0;
            typeContext = getContext(dimension.getLandMin(), dimension.getLandMax(), typeNoise);
        } else if(typeNoise >= dimension.getShoreMin() && typeNoise <= dimension.getShoreMax()){
            type = 1;
            typeContext = getContext(dimension.getShoreMin(), dimension.getShoreMax(), typeNoise);
        } else if(typeNoise >= dimension.getSeaMin() && typeNoise <= dimension.getSeaMax()){
            type = 2;
            typeContext = getContext(dimension.getSeaMin(), dimension.getSeaMax(), typeNoise);
        }

        // Calculate initial region noise
        noise.setSeed(dimension.getRegionSeed());
        double regionNoise = Math.round(noise.noise(x / dimension.getRegionZoom(), z / dimension.getRegionZoom()) *
                dimension.getPrecision()) / dimension.getPrecision();
        double parentContext = 0;
        Region region = null;
        Biome biome = null;
        for (RegionLayer regionLayer : dimension.getRegions()) {
            if (regionNoise >= regionLayer.getMin() && regionNoise <= regionLayer.getMax()) {
                region = Registry.getRegion(regionLayer.getRegionName());
                parentContext = getContext(regionLayer.getMin(), regionLayer.getMax(), regionNoise);
                break;
            }
        }
        assert region != null;
        while (biome == null){
            // Calculate new noise
            noise.setSeed(region.getSeed());
            regionNoise = Math.round(noise.noise(x / region.getZoom(), z / region.getZoom()) *
                    dimension.getPrecision()) / dimension.getPrecision();

            // Make sure to search for the correct type.
            if(type == 0){
                if(region.getContextSettings().isUseLandContext()){

                } else {
                    RegionLayer regionLayer = getRegionLayer(region.getLandRegions(), regionNoise);
                    if(regionLayer != null){
                        region = Registry.getRegion(regionLayer.getRegionName());
                        parentContext = getContext(regionLayer.getMin(), regionLayer.getMax(), regionNoise);
                    }else {
                        biome = getBiome(region.getLandBiomes(), regionNoise);
                    }
                }
            }else if(type == 1){
                if(region.getContextSettings().isUseShoreContext()){

                } else {
                    RegionLayer regionLayer = getRegionLayer(region.getShoreRegions(), regionNoise);
                    if(regionLayer != null){
                        region = Registry.getRegion(regionLayer.getRegionName());
                        parentContext = getContext(regionLayer.getMin(), regionLayer.getMax(), regionNoise);
                    }else {
                        biome = getBiome(region.getShoreBiomes(), regionNoise);
                    }
                }
            }else if (type == 2){
                if(region.getContextSettings().isUseSeaContext()){

                } else {
                    RegionLayer regionLayer = getRegionLayer(region.getSeaRegions(), regionNoise);
                    if(regionLayer != null){
                        region = Registry.getRegion(regionLayer.getRegionName());
                        parentContext = getContext(regionLayer.getMin(), regionLayer.getMax(), regionNoise);
                    }else {
                        biome = getBiome(region.getSeaBiomes(), regionNoise);
                    }
                }
            }
        }
        return biome.getId();
    }

    @Nullable
    private static RegionLayer getRegionLayer(final List<RegionLayer> layers, final double noise){
        RegionLayer resultLayer = null;
        for (RegionLayer layer : layers){
            if (noise >= layer.getMin() && noise <= layer.getMax()) {
                resultLayer = layer;
                break;
            }
        }
        return resultLayer;
    }

    @Nullable
    private static Biome getBiome(final List<BiomeLayer> layers, final double noise){
        Biome biome = null;
        for (BiomeLayer layer : layers){
            if (noise >= layer.getMin() && noise <= layer.getMax()) {
                biome = Registry.getBiome(layer.getBiomeName());
                break;
            }
        }
        return biome;
    }

    private static double getContext(double min, double max, double noise){
        final double range = max - min;
        final double value = noise - min;
        return Math.round((((value / range) * 2) - 1) * dimension.getPrecision()) / dimension.getPrecision();
    }

}
