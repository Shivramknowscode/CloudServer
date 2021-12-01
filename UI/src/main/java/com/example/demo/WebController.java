package com.example.demo;
import io.minio.MinioClient;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.util.UUID;


@Controller
public class WebController implements WebMvcConfigurer {

	@Override
	public void addViewControllers(ViewControllerRegistry registry) {
		registry.addViewController("/results").setViewName("results");
	}

//	@GetMapping("/home")
//	public String mainForm(Model model) {
//		model.addAttribute("inputNode", new InputNode());
//		return "home";
//	}
//
	@PostMapping("/home")
	public String mainSubmit(@ModelAttribute InputNode inputNode, Model model) {
		model.addAttribute("inputNode", inputNode);
		return "results";
	}

	@RequestMapping(value = "/home", method = RequestMethod.GET)
	public String home(@RequestParam(value = "userInput", required = false) String userInput,
					   @RequestParam(value = "userOperation", required = false) String userOperation,
					   Model model) {
		System.out.println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");
		System.out.println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>userOperation: " + userOperation);
		System.out.println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>userInput: " + userInput);
		model.addAttribute("userInput",userInput);
		model.addAttribute("userOperation", userOperation);

		String ENDPOINT = "http://127.0.0.1:9000";
		String ACCESSKEY = "minioadmin";
		String SECRETKEY = "minioadmin";
		String currentPendingOperation = UUID.randomUUID().toString();

		MinioClient minioClient = MinioClient.builder().endpoint(ENDPOINT).credentials(ACCESSKEY, SECRETKEY).build();
		try {
			String result = InputNode.inputSelection(minioClient, userOperation, userInput, currentPendingOperation);
			System.out.println("RESULT: " + result);
//			return  result;
		} catch (IOException e) {
			e.printStackTrace();
//			return "ERROR: " + e.getMessage();
		}


//		InputNode inputNode = new InputNode();
//		inputNode.setUserInput(String.valueOf(model.addAttribute("inputNode")));
//		model.addAttribute("InputNode", new InputNode());
//		inputNode.getUserOperation();
//		try {
//			String ret = InputNode.inputSelection(MinioClient.builder().build(), getuserOperation().toString(), inputNode.getUserInput(), UUID.randomUUID().toString());
//		} catch (IOException e) {
//			e.printStackTrace();
//		}
		return "home";
	}

//	@RequestMapping("/home")
//	public String formData(InputNode inputNode) {
//		return "/home";
//	}




//	@RequestMapping(value = "/", method = RequestMethod.POST)
//	public String getEditUserPage(@PathVariable("id") Long id, Model model, HttpServletRequest request,
//								  @RequestParam("email") String email, @RequestParam("role") String role,
//								  @RequestParam("f_name") String firstname, @RequestParam("l_name") String l_name,
//								  @RequestParam("submit") String type) {
//		// Auth
//		HttpSession session = request.getSession();
//		String ret = InputNode.inputSelection(session, "admin");
//		if (ret != null) {
//			return ret;
//		}
//
//		public String checkPersonInfo ( @ModelAttribute,BindingResult bindingResult){
//
//			if (bindingResult.hasErrors()) {
//				return "home";
//			}
//
//			return "redirect:/results";
//		}
//	}

//	@PostMapping("/")
//	public String submitForm(@ModelAttribute("inputnode") InputNode inputNode){
//
//		return null;
//	}

	@ModelAttribute("userOperation")
	public String[] getuserOperation() {
		return new String[]{
				"reverseString", "toUpperCase"
		};
	}

}
