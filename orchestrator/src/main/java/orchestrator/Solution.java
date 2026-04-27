package orchestrator;

class Solution {
    public int maxArea(int[] height) {
        int maxHeight1=0;
        int maxHeight2=0;
        int maxIndex1=0;
        int maxIndex2=0;
        for(int i=0;i<height.length;i++){
            if(height[i]+(height.length-1-i)>maxHeight1) {
                maxHeight1=height[i]+(height.length-1-i);
                maxIndex1=i;
            }else if(height[i]+(i-maxIndex1)>=maxHeight2){
                maxHeight2=height[i]+(i-maxIndex1);
                maxIndex2=i;
            }
        }
        return (maxIndex2-maxIndex1)* Integer.min(height[maxIndex1],height[maxIndex2]);
    }

    public static void main(String[] args) {
            Solution sol = new Solution();
            int[] height1 = {1,2,4,3};
            int[] height2 = {1,1};
            System.out.println("Test 1 Output: " + sol.maxArea(height1)); // Expected: 49
            System.out.println("Test 2 Output: " + sol.maxArea(height2)); // Expected: 1
        }

}
