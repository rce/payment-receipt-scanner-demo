import {DragEvent, useRef, useState} from 'react'

type Transaction = {
  transactionDate: string;
  payee: string;
  items: Item[];
  vat: Vat[];
  totalSum: number;
  currency: string;
}

type Item = {
  label: string;
  amount: number;
  category: string;
}

type Vat = {
  base: string;
  gross: number;
  net: number;
  tax: number;
}

function App() {
  const [loading, setLoading] = useState(false)
  const [transaction, setTransaction] = useState<Transaction | undefined>()
  const formRef = useRef<HTMLFormElement>(null);
  const fileInput = useRef<HTMLInputElement>(null);

  function onDragOver(e: DragEvent<HTMLDivElement>) {
    e.preventDefault()
  }

  function onDrop(e: DragEvent<HTMLDivElement>) {
    e.preventDefault();
    if (loading) return;

    if (fileInput.current && formRef.current) {
      fileInput.current.files = e.dataTransfer.files
      console.log("Submitting")
      doProcessing()
    }
  }

  function doProcessing() {
    setLoading(true)
    fetch("https://rleccerbjbyl7iyo5aamr5bebm0rayiw.lambda-url.eu-west-1.on.aws/", {
      method: "POST",
      body: new FormData(formRef.current!)
    })
      .then(async (res) => {
        setTransaction(await res.json())
        setLoading(false)
      })
      .catch((err) => {
        setLoading(false)
        console.error(err)
        setTransaction(err) // LOl
        alert(err)
      })
  }

  function onFileInputChange() {
    // TODO check mobile
    doProcessing()
  }

  return (
    <>
      <div className="dropzone" onDrop={onDrop} onDragOver={onDragOver}>
        <h1>Drag and drop receipt image</h1>
        <form ref={formRef} method={"POST"} encType={"multipart/form-data"}>
          <label>
            <input onChange={onFileInputChange} hidden={true} ref={fileInput} type={"file"} name={"image"} accept={"image/*;capture=camera"}/>
            <h3>Or add an photo by clicking here! 📷</h3>
          </label>
        </form>
        {loading && <p>Loading...</p>}
        {(!loading && transaction) && <pre>{JSON.stringify(transaction, null, 2)}</pre>}
      </div>
    </>
  )
}


export default App
