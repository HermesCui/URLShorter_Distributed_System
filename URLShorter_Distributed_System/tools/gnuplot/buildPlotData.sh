#Builds a .tsv dataset for threadcount to execution completion time in usermode 
#on cracking hash 25d55ad283aa400af464c76d713c07ad



TIMEFORMAT="%U"
UPPER_CNT=100
PLOTOUTPUT="outputresult.tsv"

> "$PLOTOUTPUT"

function write_data(){
    local thread_count=$1
    local cmd="java LoadTest 127.0.0.1 45601 11 PUT 1000"
    local executionTime="$(time ($cmd) 2>&1 1>/dev/null)"
    local rowData="$(paste <(echo $thread_count) <(echo $executionTime))"
    echo $rowData >> ${PLOTOUTPUT}
}

for ((i=1;i<=UPPER_CNT;i++)); do
    write_data $i
done

